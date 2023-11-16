package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.DocumentFile
import java.util.UUID

@Repository
interface DocumentFileRepository : JpaRepository<DocumentFile, Long> {
  fun findByDocumentUuid(documentUuid: UUID): DocumentFile?
}
