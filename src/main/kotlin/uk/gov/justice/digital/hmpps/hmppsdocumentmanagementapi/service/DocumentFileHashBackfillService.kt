package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentHashingProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Service
class DocumentFileHashBackfillService(
  private val documentRepository: DocumentRepository,
  private val documentFileService: DocumentFileService,
  private val documentDuplicateService: DocumentDuplicateService,
  private val hashingProperties: DocumentHashingProperties,
  @Value("\${document.file-hash-backfill.batch-size:200}") private val batchSize: Int,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val ZERO_UUID = UUID(0, 0)
  }

  private val running = AtomicBoolean(false)

  fun run(): Boolean {
    if (!running.compareAndSet(false, true)) {
      log.info("File hash backfill already running on this pod; ignoring request")
      return false
    }

    val types = hashingProperties.fileHashDocumentTypes
    if (types.isEmpty()) {
      log.info("File hash backfill has no configured fileHash document types; nothing to do")
      running.set(false)
      return true
    }

    var afterUuid = ZERO_UUID
    var processed = 0
    try {
      log.info("File hash backfill starting for types {}", types)
      while (true) {
        val batch = documentRepository.findBlankFileHashBatch(types, afterUuid, PageRequest.ofSize(batchSize))
        if (batch.isEmpty()) break

        batch.forEach { document ->
          runCatching { hashAndRedetermine(document) }
            .onFailure { log.error("File hash backfill failed for {}", document.documentUuid, it) }
        }

        afterUuid = batch.last().documentUuid
        processed += batch.size
        log.info("File hash backfill processed {} so far", processed)
      }
      log.info("File hash backfill complete: {} documents processed", processed)
    } finally {
      running.set(false)
    }
    return true
  }

  private fun hashAndRedetermine(document: Document) {
    val hash = documentFileService.getDocumentFile(document.documentUuid, document.documentType).use { it.sha256() }
    document.fileHash = hash
    documentRepository.save(document)
    documentDuplicateService.redetermineCanonicalFor(document)
  }

  private fun InputStream.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
