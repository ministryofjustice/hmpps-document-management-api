package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID

@Service
@Transactional
class DocumentService(
  private val documentRepository: DocumentRepository,
) {
  fun getDocument(documentUuid: UUID): Document {
    val document = documentRepository.findByDocumentUuid(documentUuid)
      ?: throw EntityNotFoundException("Document with UUID '$documentUuid' not found")

    return Document(
      document.documentUuid,
      document.documentType,
      document.filename,
      document.fileExtension,
      document.fileSize,
      document.fileHash,
      document.mimeType,
      document.metadata,
      document.createdTime,
      document.createdByServiceName,
      document.createdByUsername,
    )
  }
}
