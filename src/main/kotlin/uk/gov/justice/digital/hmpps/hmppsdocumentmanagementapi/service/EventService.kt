package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@Service
class EventService(
  private val auditService: AuditService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun recordDocumentUploadedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
    auditService.auditEvent(EventType.DOCUMENT_UPLOADED, document, documentRequestContext, document.createdTime)
  }

  fun recordDocumentRetrievedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
    auditService.auditEvent(EventType.DOCUMENT_RETRIEVED, document, documentRequestContext)
  }

  fun recordDocumentFileDownloadedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
    auditService.auditEvent(EventType.DOCUMENT_FILE_DOWNLOADED, document, documentRequestContext)
  }

  fun recordDocumentMetadataReplacedEvent(
    event: DocumentMetadataReplacedEvent,
    documentRequestContext: DocumentRequestContext,
    eventTime: LocalDateTime,
  ) {
    auditService.auditEvent(EventType.DOCUMENT_METADATA_REPLACED, event, documentRequestContext, eventTime)
  }

  fun recordDocumentDeletedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext) {
    auditService.auditEvent(EventType.DOCUMENT_DELETED, document, documentRequestContext)
  }
}
