package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResults
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

@Service
@Transactional(readOnly = true)
class DocumentSearchService(
  private val documentRepository: DocumentRepository,
  private val documentSearchSpecification: DocumentSearchSpecification,
) {
  fun searchDocuments(request: DocumentSearchRequest): DocumentSearchResults {
    // var spec =

    var spec = documentSearchSpecification.metadataContains("prisonNumber", request.metadata["prisonNumber"].asText())

    request.documentType?.apply { spec = documentSearchSpecification.prisonCodeEquals(request.documentType) }

    val results = documentRepository.findAll(spec)

    return DocumentSearchResults(
      request,
      results.toModels(),
    )
  }
}
