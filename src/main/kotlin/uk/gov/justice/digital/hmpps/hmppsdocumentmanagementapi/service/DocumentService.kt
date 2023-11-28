package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentAlreadyUploadedException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.findByDocumentUuidOrThrowNotFound
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFile as DocumentFileModel

@Service
@Transactional
class DocumentService(
  private val documentRepository: DocumentRepository,
  private val documentFileService: DocumentFileService,
) {
  fun getDocument(documentUuid: UUID): DocumentModel {
    val document = documentRepository.findByDocumentUuidOrThrowNotFound(documentUuid)

    return document.toModel()
  }

  fun getDocumentFile(documentUuid: UUID): DocumentFileModel {
    val document = getDocument(documentUuid)
    val inputStream = documentFileService.getDocumentFile(documentUuid)
    return DocumentFileModel(
      "${document.filename}.${document.fileExtension}",
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
        // TODO: filename parser to remove any path information and return an acceptable default
        filename = (file.originalFilename ?: "UNKNOWN").trim().let { it.substring(0, it.lastIndexOf('.')) },
        fileExtension = "pdf",
        fileSize = file.size,
        // TODO: Agree on a hashing algorithm and implement
        fileHash = "",
        mimeType = file.contentType ?: "application/pdf",
        metadata = metadata,
        createdByServiceName = documentRequestContext.serviceName,
        createdByUsername = documentRequestContext.username,
      ),
    )

    // Any thrown exception will cause the database transaction to roll back allowing the request to be retried
    documentFileService.saveDocumentFile(documentUuid, file)

    return document.toModel()
  }

  fun replaceDocumentMetadata(
    documentUuid: UUID,
    metadata: JsonNode,
    documentRequestContext: DocumentRequestContext,
  ): DocumentModel {
    val document = documentRepository.findByDocumentUuidOrThrowNotFound(documentUuid)

    document.replaceMetadata(
      metadata = metadata,
      supersededByServiceName = documentRequestContext.serviceName,
      supersededByUsername = documentRequestContext.username,
    )

    return documentRepository.saveAndFlush(document).toModel()
  }
}
