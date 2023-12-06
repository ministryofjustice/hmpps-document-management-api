package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@Service
class EventService(
  private val auditService: AuditService,
) {
  fun recordDocumentUploadedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
  }

  fun recordDocumentRetrievedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
  }

  fun recordDocumentFileDownloadedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
  }

  fun recordDocumentMetadataReplacedEvent(document: DocumentModel, originalMetadata: JsonNode, documentRequestContext: DocumentRequestContext) {
  }

  fun recordDocumentDeletedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
  }
}
