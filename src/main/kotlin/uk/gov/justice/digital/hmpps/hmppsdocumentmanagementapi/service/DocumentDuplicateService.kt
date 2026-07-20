package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentCanonicalProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID

/**
 * Determines the canonical document among a set of content-equivalent documents, and maintains the
 * duplicate_of pointer that the canonical search reads. Membership of a set is the transitive closure
 * over two relations, sharing an extracted-content hash or sharing a stored-byte hash, which is what
 * lets a PECS copy, a prison copy and a separately uploaded copy resolve to one set even though no
 * single pair shares both hashes. The canonical is the highest priority by configured service name,
 * then the oldest.
 *
 * This is maintained on write and delete rather than written by consumers, because the store is the
 * only place that sees every member and every insert and delete, so it can keep exactly one canonical
 * per set without any external coordination.
 */
@Service
class DocumentDuplicateService(
  private val documentRepository: DocumentRepository,
  private val canonicalProperties: DocumentCanonicalProperties,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val MAX_CLOSURE_ITERATIONS = 16
    private const val NOT_AUTHORITATIVE = Int.MAX_VALUE
  }

  fun redetermineCanonicalFor(document: Document) = redetermineCanonicalFor(document.fileContentHash, document.fileHash)

  @Transactional
  fun redetermineCanonicalFor(fileContentHash: String?, fileHash: String?) {
    val seedContentHash = fileContentHash?.takeIf { it.isNotBlank() }
    val seedFileHash = fileHash?.takeIf { it.isNotBlank() }
    val seedHashes = listOfNotNull(seedContentHash, seedFileHash)
    if (seedHashes.isEmpty()) return

    // Locks are taken in sorted order so concurrent recomputes that share a hash cannot deadlock.
    seedHashes.sorted().forEach { documentRepository.lockOnHash(it) }

    val group = findMatchingDocuments(seedContentHash, seedFileHash)
    if (group.size <= 1) {
      group.singleOrNull()?.let { setDuplicateOf(it, null) }
      return
    }

    val canonical = group.sortedWith(canonicalOrder).first()
    group.forEach { document ->
      val desired = if (document.documentUuid == canonical.documentUuid) null else canonical.documentUuid
      setDuplicateOf(document, desired)
    }
  }

  private fun setDuplicateOf(document: Document, duplicateOf: UUID?) {
    if (document.duplicateOf != duplicateOf) {
      document.duplicateOf = duplicateOf
      documentRepository.save(document)
    }
  }

  /**
   * Expands from the seed hashes, following the content and byte hashes of each document found until
   * the set stops growing, so a content match and a byte match resolve to one set even when no single
   * pair shares both. Soft-deleted documents are excluded by the entity restriction, so a deleted
   * document drops out and the survivors are re-ranked. Blank byte hashes, used by document types that
   * are not hashed, are never followed, otherwise every unhashed document would collapse into one set.
   */
  private fun findMatchingDocuments(seedContentHash: String?, seedFileHash: String?): Collection<Document> {
    val members = LinkedHashMap<UUID, Document>()
    val seenContentHashes = HashSet<String>()
    val seenFileHashes = HashSet<String>()
    var frontierContent = setOfNotNull(seedContentHash)
    var frontierFile = setOfNotNull(seedFileHash)

    var iterations = 0
    while ((frontierContent.isNotEmpty() || frontierFile.isNotEmpty()) && iterations++ < MAX_CLOSURE_ITERATIONS) {
      seenContentHashes.addAll(frontierContent)
      seenFileHashes.addAll(frontierFile)

      val found = buildMap {
        if (frontierContent.isNotEmpty()) {
          documentRepository.findByFileContentHashIn(frontierContent)
            .filter { participatesInDeduplication(it) }
            .forEach { put(it.documentUuid, it) }
        }
        if (frontierFile.isNotEmpty()) {
          documentRepository.findByFileHashIn(frontierFile)
            .filter { participatesInDeduplication(it) }
            .forEach { put(it.documentUuid, it) }
        }
      }

      val nextContent = HashSet<String>()
      val nextFile = HashSet<String>()
      found.values.forEach { document ->
        if (members.putIfAbsent(document.documentUuid, document) == null) {
          document.fileContentHash?.takeIf { it.isNotBlank() && it !in seenContentHashes }?.let { nextContent.add(it) }
          document.fileHash.takeIf { it.isNotBlank() && it !in seenFileHashes }?.let { nextFile.add(it) }
        }
      }

      frontierContent = nextContent
      frontierFile = nextFile
    }

    if (iterations >= MAX_CLOSURE_ITERATIONS && (frontierContent.isNotEmpty() || frontierFile.isNotEmpty())) {
      log.warn("Canonical closure hit iteration cap with {} members; result may be partial", members.size)
    }
    return members.values
  }

  private fun participatesInDeduplication(document: Document): Boolean {
    val activeStatuses = canonicalProperties.activeStatuses
    if (activeStatuses.isEmpty()) return true

    val status = document.metadata.path("status").asString().takeIf { it.isNotBlank() }
    return status != null && activeStatuses.any { it.equals(status, ignoreCase = true) }
  }

  private val canonicalOrder: Comparator<Document> = compareBy<Document> { authoritativeRank(it.createdByServiceName) }
    .thenBy { it.createdTime }
    .thenBy { it.documentUuid }

  private fun authoritativeRank(serviceName: String): Int = canonicalProperties.authoritativeServiceNames
    .indexOfFirst { it.equals(serviceName, ignoreCase = true) }
    .let { if (it >= 0) it else NOT_AUTHORITATIVE }
}
