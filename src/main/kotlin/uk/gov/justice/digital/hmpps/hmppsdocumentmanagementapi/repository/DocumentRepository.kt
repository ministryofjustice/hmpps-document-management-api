package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
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

  @Query("SELECT d.documentType FROM Document d WHERE d.documentUuid = :documentUuid")
  fun getDocumentTypeByDocumentUuid(documentUuid: UUID): DocumentType?
}

fun DocumentRepository.findByDocumentUuidOrThrowNotFound(documentUuid: UUID) =
  this.findByDocumentUuid(documentUuid) ?: throw EntityNotFoundException("Document with UUID '$documentUuid' not found.")
