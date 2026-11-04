package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentHashingProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceReactivateDocumentTest {
  private val documentRepository: DocumentRepository = mock()
  private val eventService: EventService = mock()
  private val documentDuplicateService: DocumentDuplicateService = mock()

  private val service = DocumentService(
    documentRepository,
    mock(),
    eventService,
    mock(),
    DocumentHashingProperties(),
    documentDuplicateService,
    mock(),
  )

  private val documentUuid = UUID.randomUUID()
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()

  private val documentRequestContext = DocumentRequestContext(
    "Reactivated using service name",
    "RSI",
    "REACTIVATED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuidIncludingSoftDeleted(documentUuid)).thenReturn(document)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.reactivateByDocumentUuid(documentUuid)).thenReturn(1)
    whenever(document.toModel()).thenReturn(documentModel)
  }

  @Test
  fun `throws entity not found exception when document not found`() {
    val missingUuid = UUID.randomUUID()

    assertThatThrownBy { service.reactivateDocument(missingUuid, documentRequestContext) }
      .isInstanceOf(EntityNotFoundException::class.java)
      .hasMessage("Document with UUID '$missingUuid' not found.")

    verify(documentRepository, never()).reactivateByDocumentUuid(any())
  }

  @Test
  fun `clears the deleted columns and returns the document`() {
    val result = service.reactivateDocument(documentUuid, documentRequestContext)

    verify(documentRepository).reactivateByDocumentUuid(documentUuid)
    assertThat(result).isSameAs(documentModel)
  }

  @Test
  fun `records event`() {
    service.reactivateDocument(documentUuid, documentRequestContext)

    verify(eventService).recordDocumentReactivatedEvent(eq(documentModel), eq(documentRequestContext), any<Long>())
  }

  @Test
  fun `redetermines canonical so the document rejoins its duplicate set`() {
    service.reactivateDocument(documentUuid, documentRequestContext)

    verify(documentDuplicateService).redetermineCanonicalFor(document)
  }

  @Test
  fun `is idempotent - a document that is not deleted is returned without an event`() {
    whenever(documentRepository.reactivateByDocumentUuid(documentUuid)).thenReturn(0)

    val result = service.reactivateDocument(documentUuid, documentRequestContext)

    assertThat(result).isSameAs(documentModel)
    verify(eventService, never()).recordDocumentReactivatedEvent(any(), any(), any())
    verify(documentDuplicateService, never()).redetermineCanonicalFor(any<Document>())
  }
}
