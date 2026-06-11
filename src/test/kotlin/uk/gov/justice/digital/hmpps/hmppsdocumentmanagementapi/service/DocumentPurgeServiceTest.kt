package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DocumentPurgeServiceTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentFileService: DocumentFileService = mock()
  private val purgeBatchExecutor: DocumentPurgeBatchExecutor = mock()

  private val service = DocumentPurgeService(
    documentRepository,
    documentFileService,
    purgeBatchExecutor,
    batchSize = 2,
  )

  private val cutoffDate = LocalDate.of(2024, 1, 1)
  private val targetTypes = setOf(DocumentType.HMCTS_WARRANT, DocumentType.PRISON_COURT_REGISTER)

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findPurgeableBatch(any(), any(), any(), any())).thenReturn(emptyList())
  }

  @Test
  fun `does nothing when no documents match`() {
    val result = service.purge(targetTypes, cutoffDate)

    assertThat(result.started).isTrue()
    assertThat(result.purgedCount).isEqualTo(0)
    assertThat(result.s3FailureCount).isEqualTo(0)
    verify(purgeBatchExecutor, never()).purgeBatch(any(), any())
    verify(documentFileService, never()).deleteDocumentFile(any(), any())
  }

  @Test
  fun `passes cutoff as start-of-day and types as enum names to repository`() {
    val cutoffCaptor = argumentCaptor<LocalDateTime>()
    val typesCaptor = argumentCaptor<Collection<String>>()
    whenever(documentRepository.findPurgeableBatch(typesCaptor.capture(), cutoffCaptor.capture(), any(), any()))
      .thenReturn(emptyList())

    service.purge(targetTypes, cutoffDate)

    assertThat(cutoffCaptor.firstValue).isEqualTo(cutoffDate.atStartOfDay())
    assertThat(typesCaptor.firstValue).containsExactlyInAnyOrder("HMCTS_WARRANT", "PRISON_COURT_REGISTER")
  }

  @Test
  fun `purges a single batch - DB executor first, then S3 for each document`() {
    val doc1 = makeDocument(documentId = 1L, documentType = DocumentType.HMCTS_WARRANT)
    val doc2 = makeDocument(documentId = 2L, documentType = DocumentType.PRISON_COURT_REGISTER)
    whenever(documentRepository.findPurgeableBatch(any(), any(), any(), any()))
      .thenReturn(listOf(doc1, doc2))
      .thenReturn(emptyList())

    val result = service.purge(targetTypes, cutoffDate)

    assertThat(result.purgedCount).isEqualTo(2)
    assertThat(result.s3FailureCount).isEqualTo(0)

    val idsCaptor = argumentCaptor<List<Long>>()
    val uuidsCaptor = argumentCaptor<List<UUID>>()
    verify(purgeBatchExecutor).purgeBatch(idsCaptor.capture(), uuidsCaptor.capture())
    assertThat(idsCaptor.firstValue).containsExactly(1L, 2L)
    assertThat(uuidsCaptor.firstValue).containsExactly(doc1.documentUuid, doc2.documentUuid)

    verify(documentFileService).deleteDocumentFile(doc1.documentUuid, DocumentType.HMCTS_WARRANT)
    verify(documentFileService).deleteDocumentFile(doc2.documentUuid, DocumentType.PRISON_COURT_REGISTER)
  }

  @Test
  fun `advances cursor between batches using the last document_id`() {
    val batch1Doc1 = makeDocument(documentId = 1L)
    val batch1Doc2 = makeDocument(documentId = 2L)
    val batch2Doc1 = makeDocument(documentId = 5L)

    val afterIdCaptor = argumentCaptor<Long>()
    whenever(documentRepository.findPurgeableBatch(any(), any(), afterIdCaptor.capture(), any()))
      .thenReturn(listOf(batch1Doc1, batch1Doc2))
      .thenReturn(listOf(batch2Doc1))
      .thenReturn(emptyList())

    val result = service.purge(targetTypes, cutoffDate)

    assertThat(result.purgedCount).isEqualTo(3)
    // 0 for the first lookup, then the last id of each non-empty batch
    assertThat(afterIdCaptor.allValues).containsExactly(0L, 2L, 5L)
  }

  @Test
  fun `counts S3 failures and continues - DB purge still completes for the batch`() {
    val doc1 = makeDocument(documentId = 1L)
    val doc2 = makeDocument(documentId = 2L)
    whenever(documentRepository.findPurgeableBatch(any(), any(), any(), any()))
      .thenReturn(listOf(doc1, doc2))
      .thenReturn(emptyList())

    doThrow(RuntimeException("S3 unavailable"))
      .whenever(documentFileService).deleteDocumentFile(eq(doc1.documentUuid), any())

    val result = service.purge(targetTypes, cutoffDate)

    assertThat(result.purgedCount).isEqualTo(2)
    assertThat(result.s3FailureCount).isEqualTo(1)

    verify(purgeBatchExecutor).purgeBatch(listOf(1L, 2L), listOf(doc1.documentUuid, doc2.documentUuid))
    verify(documentFileService).deleteDocumentFile(doc2.documentUuid, doc2.documentType)
  }

  @Test
  fun `returns not-started when a purge is already running on this pod`() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    // The first purge blocks inside the repository lookup while holding the running flag.
    whenever(documentRepository.findPurgeableBatch(any(), any(), any(), any())).thenAnswer {
      entered.countDown()
      release.await()
      emptyList<Document>()
    }

    val pool = Executors.newSingleThreadExecutor()
    try {
      val first = pool.submit<DocumentPurgeResult> { service.purge(targetTypes, cutoffDate) }

      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
      val second = service.purge(targetTypes, cutoffDate)
      assertThat(second.started).isFalse()

      release.countDown()
      assertThat(first.get(5, TimeUnit.SECONDS).started).isTrue()
    } finally {
      release.countDown()
      pool.shutdownNow()
    }
  }

  @Test
  fun `releases the running flag after an exception so a later run can proceed`() {
    whenever(documentRepository.findPurgeableBatch(any(), any(), any(), any()))
      .thenThrow(RuntimeException("DB error"))

    runCatching { service.purge(targetTypes, cutoffDate) }

    whenever(documentRepository.findPurgeableBatch(any(), any(), any(), any())).thenReturn(emptyList())
    assertThat(service.purge(targetTypes, cutoffDate).started).isTrue()
  }

  private fun makeDocument(
    documentId: Long = 0L,
    documentType: DocumentType = DocumentType.HMCTS_WARRANT,
  ) = Document(
    documentId = documentId,
    documentUuid = UUID.randomUUID(),
    documentType = documentType,
    filename = "test",
    fileExtension = "pdf",
    fileSize = 1024,
    fileHash = "",
    mimeType = "application/pdf",
    metadata = ObjectMapper().readTree("{}"),
    createdTime = LocalDateTime.now().minusYears(1),
    createdByServiceName = "test-service",
    createdByUsername = "TEST_USER",
  )
}
