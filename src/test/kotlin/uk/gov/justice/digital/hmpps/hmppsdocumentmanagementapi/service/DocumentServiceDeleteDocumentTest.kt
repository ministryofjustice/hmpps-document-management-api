package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceDeleteDocumentTest {
  private val documentRepository: DocumentRepository = mock()
  private val eventService: EventService = mock()

  private val service = DocumentService(documentRepository, mock(), eventService)

  private val documentUuid = UUID.randomUUID()
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()

  private val documentRequestContext = DocumentRequestContext(
    "Deleted using service name",
    "RSI",
    "DELETED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())
    whenever(document.toModel()).thenReturn(documentModel)
  }

  @Test
  fun `does not throw exception when document not found`() {
    service.deleteDocument(UUID.randomUUID(), documentRequestContext)

    verify(documentRepository, never()).saveAndFlush(any<Document>())
  }

  @Test
  fun `calls document replace metadata function`() {
    service.deleteDocument(documentUuid, documentRequestContext)

    verify(document).delete(
      any<LocalDateTime>(),
      eq(documentRequestContext.serviceName),
      eq(documentRequestContext.username),
    )
  }

  @Test
  fun `saves and flushes document`() {
    service.deleteDocument(documentUuid, documentRequestContext)

    verify(documentRepository).saveAndFlush(document)
  }

  @Test
  fun `records event`() {
    service.deleteDocument(documentUuid, documentRequestContext)

    verify(eventService).recordDocumentDeletedEvent(eq(documentModel), eq(documentRequestContext), any<Long>())
  }
}
