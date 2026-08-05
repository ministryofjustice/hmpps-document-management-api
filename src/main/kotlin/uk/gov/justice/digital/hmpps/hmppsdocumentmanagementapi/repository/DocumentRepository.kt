package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface DocumentRepository :
  JpaRepository<Document, Long>,
  JpaSpecificationExecutor<Document> {

  @EntityGraph(attributePaths = ["documentMetadataHistory"])
  fun findByDocumentUuid(documentUuid: UUID): Document?

  @Query(
    value = "SELECT * FROM document d WHERE d.document_uuid = :documentUuid",
    nativeQuery = true,
  )
  fun findByDocumentUuidIncludingSoftDeleted(documentUuid: UUID): Document?

  fun findByDocumentUuidIn(documentUuid: Collection<UUID>): List<Document>

  @Query("SELECT d.documentType FROM Document d WHERE d.documentUuid = :documentUuid")
  fun getDocumentTypeByDocumentUuid(documentUuid: UUID): DocumentType?

  fun findByFileContentHashIn(fileContentHashes: Collection<String>): List<Document>

  fun findByFileHashIn(fileHashes: Collection<String>): List<Document>

  @Query(
    value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:hash, 0))) AS lock_acquired",
    nativeQuery = true,
  )
  fun lockOnHash(hash: String): Int

  @Query(
    "SELECT d FROM Document d WHERE d.documentType IN :documentTypes " +
      "AND d.fileHash = '' AND d.documentUuid > :afterUuid ORDER BY d.documentUuid",
  )
  fun findBlankFileHashBatch(
    documentTypes: Collection<DocumentType>,
    afterUuid: UUID,
    pageable: Pageable,
  ): List<Document>

  @Query(
    value = "SELECT * FROM document WHERE document_type IN (:documentTypes) AND created_time < :before AND document_id > :afterId ORDER BY document_id LIMIT :batchSize",
    nativeQuery = true,
  )
  fun findPurgeableBatch(
    documentTypes: Collection<String>,
    before: LocalDateTime,
    afterId: Long,
    batchSize: Int,
  ): List<Document>

  @Modifying
  @Query(
    value = "UPDATE document SET duplicate_of = NULL WHERE duplicate_of IN (:uuids)",
    nativeQuery = true,
  )
  fun clearDuplicateOfPointersTo(uuids: Collection<UUID>)

  @Modifying
  @Query(
    value = "DELETE FROM document_metadata_history WHERE document_id IN (:documentIds)",
    nativeQuery = true,
  )
  fun deleteMetadataHistoryByDocumentIds(documentIds: Collection<Long>)

  @Modifying
  @Query(
    value = "DELETE FROM document WHERE document_id IN (:documentIds)",
    nativeQuery = true,
  )
  fun deleteByDocumentIds(documentIds: Collection<Long>)
}

fun DocumentRepository.findByDocumentUuidOrThrowNotFound(documentUuid: UUID) = this.findByDocumentUuid(documentUuid) ?: throw EntityNotFoundException("Document with UUID '$documentUuid' not found.")
