package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import java.util.UUID

@Repository
interface DocumentRepository : JpaRepository<Document, Long> {
  fun findByDocumentUuid(documentUuid: UUID): Document?
}
