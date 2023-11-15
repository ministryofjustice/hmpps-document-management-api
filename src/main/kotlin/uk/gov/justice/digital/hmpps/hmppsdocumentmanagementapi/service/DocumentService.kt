package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.DocumentFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentFileRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@Service
@Transactional
class DocumentService(
  private val documentRepository: DocumentRepository,
  private val documentFileRepository: DocumentFileRepository,
) {
  fun getDocument(documentUuid: UUID): DocumentModel {
    val document = documentRepository.findByDocumentUuid(documentUuid)
      ?: throw EntityNotFoundException("Document with UUID '$documentUuid' not found")

    return document.toModel()
  }

  fun uploadDocument(
    documentType: DocumentType,
    documentUuid: UUID,
    file: MultipartFile,
    metadata: JsonNode,
  ): DocumentModel {
    // TODO: UUID check should include soft deleted documents
    require(documentRepository.findByDocumentUuid(documentUuid) == null) {
      "Document with UUID '$documentUuid' already exists in service"
    }

    documentFileRepository.saveAndFlush(
      DocumentFile(
        documentUuid = documentUuid,
        fileData = file.bytes,
      ),
    )

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
        createdByServiceName = "Remand and Sentencing",
        createdByUsername = "CREATED_BY_USER",
      ),
    )

    return document.toModel()
  }
}
