package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.toCustomEventMetrics
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.toCustomEventProperties
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@Service
class EventService(
  private val auditService: AuditService,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun recordDocumentUploadedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext, eventTimeMs: Long) {
    log.info("Document uploaded {}", document)
    auditService.auditEvent(EventType.DOCUMENT_UPLOADED, document, documentRequestContext, document.createdTime)
    telemetryClient.trackEvent(
      EventType.DOCUMENT_UPLOADED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  fun recordDocumentRetrievedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext, eventTimeMs: Long) {
    log.info("Document retrieved {}", document)
    auditService.auditEvent(EventType.DOCUMENT_RETRIEVED, document, documentRequestContext)
    telemetryClient.trackEvent(
      EventType.DOCUMENT_RETRIEVED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  fun recordDocumentFileDownloadedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext, eventTimeMs: Long) {
    log.info("Document file downloaded {}", document)
    auditService.auditEvent(EventType.DOCUMENT_FILE_DOWNLOADED, document, documentRequestContext)
    telemetryClient.trackEvent(
      EventType.DOCUMENT_FILE_DOWNLOADED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  fun recordDocumentMetadataReplacedEvent(
    event: DocumentMetadataReplacedEvent,
    documentRequestContext: DocumentRequestContext,
    eventTime: LocalDateTime,
    eventTimeMs: Long,
  ) {
    log.info("Document metadata replaced {}", event)
    auditService.auditEvent(EventType.DOCUMENT_METADATA_REPLACED, event, documentRequestContext, eventTime)
    telemetryClient.trackEvent(
      EventType.DOCUMENT_METADATA_REPLACED.name,
      event.document.toCustomEventProperties(documentRequestContext),
      event.toCustomEventMetrics(eventTimeMs),
    )
  }

  fun recordDocumentDeletedEvent(document: DocumentModel, documentRequestContext: DocumentRequestContext, eventTimeMs: Long) {
    log.info("Document deleted {}", document)
    auditService.auditEvent(EventType.DOCUMENT_DELETED, document, documentRequestContext)
    telemetryClient.trackEvent(
      EventType.DOCUMENT_DELETED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }
}
