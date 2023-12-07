package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class EventServiceTest {
  private val auditService: AuditService = mock()

  private val service = EventService(auditService)

  private val document = mock<DocumentModel>()

  private val documentRequestContext = DocumentRequestContext(
    "Service name",
    "USERNAME",
  )

  @Test
  fun `record document uploaded audits event`() {
    val createdTime = LocalDateTime.now()
    whenever(document.createdTime).thenReturn(createdTime)

    service.recordDocumentUploadedEvent(document, documentRequestContext)

    verify(auditService).auditEvent(EventType.DOCUMENT_UPLOADED, document, documentRequestContext, createdTime)
  }

  @Test
  fun `record document retrieved audits event`() {
    service.recordDocumentRetrievedEvent(document, documentRequestContext)

    verify(auditService).auditEvent(eq(EventType.DOCUMENT_RETRIEVED), eq(document), eq(documentRequestContext), any<LocalDateTime>())
  }

  @Test
  fun `record document file downloaded audits event`() {
    service.recordDocumentFileDownloadedEvent(document, documentRequestContext)

    verify(auditService).auditEvent(eq(EventType.DOCUMENT_FILE_DOWNLOADED), eq(document), eq(documentRequestContext), any<LocalDateTime>())
  }

  @Test
  fun `record document metadata replaced audits event`() {
    val event = DocumentMetadataReplacedEvent(document, mock())
    val supersededTime = LocalDateTime.now()

    service.recordDocumentMetadataReplacedEvent(event, documentRequestContext, supersededTime)

    verify(auditService).auditEvent(EventType.DOCUMENT_METADATA_REPLACED, event, documentRequestContext, supersededTime)
  }

  @Test
  fun `record document deleted audits event`() {
    service.recordDocumentDeletedEvent(document, documentRequestContext)

    verify(auditService).auditEvent(eq(EventType.DOCUMENT_DELETED), eq(document), eq(documentRequestContext), any<LocalDateTime>())
  }
}
