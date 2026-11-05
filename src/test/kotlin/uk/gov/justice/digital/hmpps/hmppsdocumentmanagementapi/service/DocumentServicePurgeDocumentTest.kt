package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentHashingProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServicePurgeDocumentTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentFileService: DocumentFileService = mock()
  private val eventService: EventService = mock()
  private val documentDuplicateService: DocumentDuplicateService = mock()
  private val purgeBatchExecutor: DocumentPurgeBatchExecutor = mock()

  private val service = DocumentService(
    documentRepository,
    documentFileService,
    eventService,
    mock(),
    DocumentHashingProperties(),
    documentDuplicateService,
    purgeBatchExecutor,
  )

  private val documentUuid = UUID.randomUUID()
  private val documentId = 42L
  private val documentType = DocumentType.HMCTS_WARRANT
  private val fileHash = "d58e3582afa99040e27b92b13c8f2280"
  private val fileContentHash = "ab12cd34"
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()

  private val documentRequestContext = DocumentRequestContext(
    "Purged using service name",
    "RSI",
    "PURGED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuidIncludingSoftDeleted(documentUuid)).thenReturn(document)
    whenever(document.documentId).thenReturn(documentId)
    whenever(document.documentUuid).thenReturn(documentUuid)
    whenever(document.documentType).thenReturn(documentType)
    whenever(document.fileHash).thenReturn(fileHash)
    whenever(document.fileContentHash).thenReturn(fileContentHash)
    whenever(document.toModel()).thenReturn(documentModel)
  }

  @Test
  fun `does not throw exception when document not found`() {
    service.purgeDocument(UUID.randomUUID(), documentRequestContext)

    verify(purgeBatchExecutor, never()).purgeBatch(any(), any())
    verify(documentFileService, never()).deleteDocumentFile(any(), any())
    verify(eventService, never()).recordDocumentPurgedEvent(any(), any(), any())
  }

  @Test
  fun `removes the stored file before database rows`() {
    service.purgeDocument(documentUuid, documentRequestContext)

    inOrder(documentFileService, purgeBatchExecutor) {
      verify(documentFileService).deleteDocumentFile(documentUuid, documentType)
      verify(purgeBatchExecutor).purgeBatch(listOf(documentId), listOf(documentUuid))
    }
  }

  @Test
  fun `records event`() {
    service.purgeDocument(documentUuid, documentRequestContext)

    verify(eventService).recordDocumentPurgedEvent(eq(documentModel), eq(documentRequestContext), any<Long>())
  }

  @Test
  fun `redetermines canonical for the purged document hashes`() {
    service.purgeDocument(documentUuid, documentRequestContext)

    verify(documentDuplicateService).redetermineCanonicalFor(fileContentHash, fileHash)
  }

  @Test
  fun `retains database rows and reports failure when stored file deletion fails`() {
    val exception = RuntimeException("S3 unavailable")
    whenever(documentFileService.deleteDocumentFile(documentUuid, documentType)).doThrow(exception)

    assertThatThrownBy {
      service.purgeDocument(documentUuid, documentRequestContext)
    }.isSameAs(exception)

    verify(purgeBatchExecutor, never()).purgeBatch(any(), any())
    verify(eventService).recordDocumentPurgeFailedEvent(
      eq(documentModel),
      eq(documentRequestContext),
      any<Long>(),
      eq("STORED_FILE_DELETE"),
      eq(exception),
    )
    verify(eventService, never()).recordDocumentPurgedEvent(any(), any(), any())
    verify(documentDuplicateService, never())
      .redetermineCanonicalFor(any<String>(), any<String>())
  }

  @Test
  fun `reports database failure after stored file has been deleted`() {
    val exception = RuntimeException("Database unavailable")
    doThrow(exception)
      .whenever(purgeBatchExecutor)
      .purgeBatch(listOf(documentId), listOf(documentUuid))

    assertThatThrownBy {
      service.purgeDocument(documentUuid, documentRequestContext)
    }.isSameAs(exception)

    verify(documentFileService).deleteDocumentFile(documentUuid, documentType)
    verify(eventService).recordDocumentPurgeFailedEvent(
      eq(documentModel),
      eq(documentRequestContext),
      any<Long>(),
      eq("DATABASE_DELETE"),
      eq(exception),
    )
    verify(eventService, never()).recordDocumentPurgedEvent(any(), any(), any())
    verify(documentDuplicateService, never())
      .redetermineCanonicalFor(any<String>(), any<String>())
  }

  @Test
  fun `does not mask the original purge failure when failure telemetry also fails`() {
    val purgeException = RuntimeException("S3 unavailable")
    whenever(documentFileService.deleteDocumentFile(documentUuid, documentType)).doThrow(purgeException)

    whenever(
      eventService.recordDocumentPurgeFailedEvent(any(), any(), any(), any(), any()),
    ).doThrow(RuntimeException("Telemetry unavailable"))

    assertThatThrownBy {
      service.purgeDocument(documentUuid, documentRequestContext)
    }.isSameAs(purgeException)
  }
}
