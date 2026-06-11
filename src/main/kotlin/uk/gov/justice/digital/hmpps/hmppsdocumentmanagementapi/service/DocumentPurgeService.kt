package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

@Service
class DocumentPurgeService(
  private val documentRepository: DocumentRepository,
  private val documentFileService: DocumentFileService,
  private val purgeBatchExecutor: DocumentPurgeBatchExecutor,
  @Value("\${document.purge.batch-size:100}") private val batchSize: Int,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val running = AtomicBoolean(false)

  fun purge(documentTypes: Set<DocumentType>, before: LocalDate): DocumentPurgeResult {
    if (!running.compareAndSet(false, true)) {
      log.info("Document purge already running on this pod; ignoring request")
      return DocumentPurgeResult(started = false)
    }

    val cutoff = before.atStartOfDay()
    var afterId = 0L
    var purgedCount = 0
    var s3FailureCount = 0

    try {
      log.info("Document purge starting: types={}, before={}, batchSize={}", documentTypes, cutoff, batchSize)

      while (true) {
        val batch = documentRepository.findPurgeableBatch(
          documentTypes = documentTypes.map { it.name },
          before = cutoff,
          afterId = afterId,
          batchSize = batchSize,
        )
        if (batch.isEmpty()) break

        val batchIds = batch.map { it.documentId }
        val batchUuids = batch.map { it.documentUuid }
        val batchTypes = batch.map { it.documentUuid to it.documentType }

        purgeBatchExecutor.purgeBatch(batchIds, batchUuids)

        batchTypes.forEach { (uuid, documentType) ->
          runCatching { documentFileService.deleteDocumentFile(uuid, documentType) }
            .onFailure {
              s3FailureCount++
              log.warn("Failed to delete S3 object for document {} ({}); orphaned object may remain", uuid, documentType, it)
            }
        }

        afterId = batch.last().documentId
        purgedCount += batch.size
        log.info("Document purge progress: {} documents processed so far", purgedCount)
      }

      log.info("Document purge complete: purgedCount={}, s3FailureCount={}", purgedCount, s3FailureCount)
    } finally {
      running.set(false)
    }

    return DocumentPurgeResult(started = true, purgedCount = purgedCount, s3FailureCount = s3FailureCount)
  }
}

data class DocumentPurgeResult(
  val started: Boolean,
  val purgedCount: Int = 0,
  val s3FailureCount: Int = 0,
)
