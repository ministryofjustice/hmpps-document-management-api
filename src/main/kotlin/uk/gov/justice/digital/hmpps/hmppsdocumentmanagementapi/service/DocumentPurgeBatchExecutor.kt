package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID

@Component
class DocumentPurgeBatchExecutor(
  private val documentRepository: DocumentRepository,
) {
  @Transactional
  fun purgeBatch(documentIds: List<Long>, documentUuids: List<UUID>) {
    documentRepository.clearDuplicateOfPointersTo(documentUuids)
    documentRepository.deleteMetadataHistoryByDocumentIds(documentIds)
    documentRepository.deleteByDocumentIds(documentIds)
  }
}
