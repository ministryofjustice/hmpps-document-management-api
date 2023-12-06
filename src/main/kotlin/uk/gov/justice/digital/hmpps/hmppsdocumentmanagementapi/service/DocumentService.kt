package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentAlreadyUploadedException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.findByDocumentUuidOrThrowNotFound
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils.fileExtension
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils.filenameWithoutExtension
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils.mimeType
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFile as DocumentFileModel

@Service
@Transactional
class DocumentService(
  private val documentRepository: DocumentRepository,
  private val documentFileService: DocumentFileService,
  private val eventService: EventService,
) {
  fun getDocument(documentUuid: UUID, documentRequestContext: DocumentRequestContext): DocumentModel {
    val document = documentRepository.findByDocumentUuidOrThrowNotFound(documentUuid)

    return document.toModel().also {
      eventService.recordDocumentRetrievedEvent(it, documentRequestContext)
    }
  }

  fun getDocumentFile(documentUuid: UUID, documentRequestContext: DocumentRequestContext): DocumentFileModel {
    val document = documentRepository.findByDocumentUuidOrThrowNotFound(documentUuid).toModel()
    val inputStream = documentFileService.getDocumentFile(documentUuid)
    eventService.recordDocumentFileDownloadedEvent(document, documentRequestContext)
    return DocumentFileModel(
      document.documentFilename,
      document.fileSize,
      document.mimeType,
      inputStream,
    )
  }

  fun uploadDocument(
    documentType: DocumentType,
    documentUuid: UUID,
    file: MultipartFile,
    metadata: JsonNode,
    documentRequestContext: DocumentRequestContext,
  ): DocumentModel {
    if (documentRepository.findByDocumentUuidIncludingSoftDeleted(documentUuid) != null) {
      throw DocumentAlreadyUploadedException(documentUuid)
    }

    // Save document first to "reserve" UUID
    val document = documentRepository.saveAndFlush(
      Document(
        documentUuid = documentUuid,
        documentType = documentType,
        filename = file.filenameWithoutExtension(documentType),
        fileExtension = file.fileExtension(documentType),
        fileSize = file.size,
        // TODO: Agree on a hashing algorithm and implement
        fileHash = "",
        mimeType = file.mimeType(),
        metadata = metadata,
        createdByServiceName = documentRequestContext.serviceName,
        createdByUsername = documentRequestContext.username,
      ),
    )

    // Any thrown exception will cause the database transaction to roll back allowing the request to be retried
    documentFileService.saveDocumentFile(documentUuid, file)

    return document.toModel().also {
      eventService.recordDocumentUploadedEvent(it, documentRequestContext)
    }
  }

  fun replaceDocumentMetadata(
    documentUuid: UUID,
    metadata: JsonNode,
    documentRequestContext: DocumentRequestContext,
  ): DocumentModel {
    val document = documentRepository.findByDocumentUuidOrThrowNotFound(documentUuid)

    val originalMetadata = document.metadata

    val metadataHistory = document.replaceMetadata(
      metadata = metadata,
      supersededByServiceName = documentRequestContext.serviceName,
      supersededByUsername = documentRequestContext.username,
    )

    return documentRepository.saveAndFlush(document).toModel().also {
      eventService.recordDocumentMetadataReplacedEvent(
        DocumentMetadataReplacedEvent(it, originalMetadata),
        documentRequestContext,
        metadataHistory.supersededTime,
      )
    }
  }

  fun deleteDocument(
    documentUuid: UUID,
    documentRequestContext: DocumentRequestContext,
  ) {
    val document = documentRepository.findByDocumentUuid(documentUuid)

    document?.apply {
      delete(
        deletedByServiceName = documentRequestContext.serviceName,
        deletedByUsername = documentRequestContext.username,
      )
      documentRepository.saveAndFlush(this).toModel().also {
        eventService.recordDocumentDeletedEvent(it, documentRequestContext)
      }
    }
  }
}
