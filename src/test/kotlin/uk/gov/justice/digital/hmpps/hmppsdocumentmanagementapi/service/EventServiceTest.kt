package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.microsoft.applicationinsights.TelemetryClient
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.toCustomEventMetrics
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.toCustomEventProperties
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class EventServiceTest {
  private val auditService: AuditService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val service = EventService(auditService, telemetryClient)

  private val document = DocumentModel(
    documentUuid = UUID.randomUUID(),
    documentType = DocumentType.HMCTS_WARRANT,
    documentFilename = "warrant_for_sentencing.pdf",
    filename = "warrant_for_sentencing",
    fileExtension = "pdf",
    fileSize = 3876,
    fileHash = "d58e3582afa99040e27b92b13c8f2280",
    mimeType = "application/pdf",
    metadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\", \"court\": \"Stafford Crown\", \"warrantDate\": \"2021-09-27\" }"),
    createdTime = LocalDateTime.now(),
    createdByServiceName = "Remand and sentencing",
    createdByUsername = "CREATED_BY_USERNAME",
  )

  private val documentRequestContext = DocumentRequestContext(
    "Service name",
    "RSI",
    "USERNAME",
  )

  private val eventTimeMs = 100L

  @Test
  fun `record document uploaded audits event`() {
    service.recordDocumentUploadedEvent(document, documentRequestContext, eventTimeMs)

    verify(auditService).auditEvent(EventType.DOCUMENT_UPLOADED, document, documentRequestContext, document.createdTime)
  }

  @Test
  fun `record document uploaded tracks event`() {
    service.recordDocumentUploadedEvent(document, documentRequestContext, eventTimeMs)

    verify(telemetryClient).trackEvent(
      EventType.DOCUMENT_UPLOADED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  @Test
  fun `record document retrieved audits event`() {
    service.recordDocumentRetrievedEvent(document, documentRequestContext, eventTimeMs)

    verify(auditService).auditEvent(eq(EventType.DOCUMENT_RETRIEVED), eq(document), eq(documentRequestContext), any<LocalDateTime>())
  }

  @Test
  fun `record document retrieved tracks event`() {
    service.recordDocumentRetrievedEvent(document, documentRequestContext, eventTimeMs)

    verify(telemetryClient).trackEvent(
      EventType.DOCUMENT_RETRIEVED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  @Test
  fun `record document file downloaded audits event`() {
    service.recordDocumentFileDownloadedEvent(document, documentRequestContext, eventTimeMs)

    verify(auditService).auditEvent(eq(EventType.DOCUMENT_FILE_DOWNLOADED), eq(document), eq(documentRequestContext), any<LocalDateTime>())
  }

  @Test
  fun `record document file downloaded tracks event`() {
    service.recordDocumentFileDownloadedEvent(document, documentRequestContext, eventTimeMs)

    verify(telemetryClient).trackEvent(
      EventType.DOCUMENT_FILE_DOWNLOADED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  @Test
  fun `record document metadata replaced audits event`() {
    val event = DocumentMetadataReplacedEvent(document, mock())
    val supersededTime = LocalDateTime.now()

    service.recordDocumentMetadataReplacedEvent(event, documentRequestContext, supersededTime, eventTimeMs)

    verify(auditService).auditEvent(EventType.DOCUMENT_METADATA_REPLACED, event, documentRequestContext, supersededTime)
  }

  @Test
  fun `record document metadata replaced tracks event`() {
    val originalMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }")
    val event = DocumentMetadataReplacedEvent(document, originalMetadata)
    val supersededTime = LocalDateTime.now()

    service.recordDocumentMetadataReplacedEvent(event, documentRequestContext, supersededTime, eventTimeMs)

    verify(telemetryClient).trackEvent(
      EventType.DOCUMENT_METADATA_REPLACED.name,
      document.toCustomEventProperties(documentRequestContext),
      event.toCustomEventMetrics(eventTimeMs),
    )
  }

  @Test
  fun `record document deleted audits event`() {
    service.recordDocumentDeletedEvent(document, documentRequestContext, eventTimeMs)

    verify(auditService).auditEvent(eq(EventType.DOCUMENT_DELETED), eq(document), eq(documentRequestContext), any<LocalDateTime>())
  }

  @Test
  fun `record document deleted tracks event`() {
    service.recordDocumentDeletedEvent(document, documentRequestContext, eventTimeMs)

    verify(telemetryClient).trackEvent(
      EventType.DOCUMENT_DELETED.name,
      document.toCustomEventProperties(documentRequestContext),
      document.toCustomEventMetrics(eventTimeMs),
    )
  }

  @Test
  fun `record documents searched audits event`() {
    val event = DocumentsSearchedEvent(
      DocumentSearchRequest(DocumentType.HMCTS_WARRANT, JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")),
      10,
      13,
    )

    service.recordDocumentsSearchedEvent(event, documentRequestContext, eventTimeMs)

    verify(auditService).auditEvent(eq(EventType.DOCUMENTS_SEARCHED), eq(event), eq(documentRequestContext), any<LocalDateTime>())
  }

  @Test
  fun `record documents searched tracks event`() {
    val event = DocumentsSearchedEvent(
      DocumentSearchRequest(DocumentType.HMCTS_WARRANT, JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")),
      10,
      13,
    )

    service.recordDocumentsSearchedEvent(event, documentRequestContext, eventTimeMs)

    verify(telemetryClient).trackEvent(
      EventType.DOCUMENTS_SEARCHED.name,
      event.toCustomEventProperties(documentRequestContext),
      event.toCustomEventMetrics(eventTimeMs),
    )
  }
}
