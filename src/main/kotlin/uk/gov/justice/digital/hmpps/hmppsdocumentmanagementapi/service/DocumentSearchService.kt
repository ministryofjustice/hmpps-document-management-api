package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@Service
@Transactional(readOnly = true)
class DocumentSearchService(
  private val documentRepository: DocumentRepository,
  private val documentSearchSpecification: DocumentSearchSpecification,
) {
  fun searchDocuments(request: DocumentSearchRequest): Collection<DocumentModel> {
    var spec = documentSearchSpecification.prisonCodeEquals(request.documentType!!)

    /*with(request) {
      documentType?.apply {
        spec = documentSearchSpecification.prisonCodeEquals(documentType)
      }
    }*/

    val results = documentRepository.findAll(spec)

    return results.toModels()
  }
}
