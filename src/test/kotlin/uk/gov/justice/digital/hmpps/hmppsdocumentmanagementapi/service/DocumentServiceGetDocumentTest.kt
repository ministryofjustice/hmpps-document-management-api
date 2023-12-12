package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceGetDocumentTest {
  private val documentRepository: DocumentRepository = mock()
  private val eventService: EventService = mock()

  private val service = DocumentService(documentRepository, mock(), eventService)

  private val documentUuid = UUID.randomUUID()
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()

  private val documentRequestContext = DocumentRequestContext(
    "Retrieved using service name",
    "MDI",
    "RETRIEVED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(document.toModel()).thenReturn(documentModel)
  }

  @Test
  fun `throws exception when document not found`() {
    UUID.randomUUID().apply {
      assertThrows<EntityNotFoundException>("Document with UUID '$this' not found.") {
        service.getDocument(this, documentRequestContext)
      }
    }
  }

  @Test
  fun `finds document by unique identifier`() {
    service.getDocument(documentUuid, documentRequestContext)

    verify(documentRepository).findByDocumentUuid(documentUuid)
  }

  @Test
  fun `records event`() {
    service.getDocument(documentUuid, documentRequestContext)

    verify(eventService).recordDocumentRetrievedEvent(eq(documentModel), eq(documentRequestContext), any<Long>())
  }

  @Test
  fun `returns document model`() {
    assertThat(service.getDocument(documentUuid, documentRequestContext)).isEqualTo(documentModel)

    verify(document).toModel()
  }
}
