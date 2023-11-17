package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

@Service
@Transactional(readOnly = true)
class DocumentSearchService(
  private val documentRepository: DocumentRepository,
  private val documentSearchSpecification: DocumentSearchSpecification,
) {
  fun searchDocuments(request: DocumentSearchRequest): DocumentSearchResult {
    var spec = documentSearchSpecification.prisonCodeEquals(request.documentType)

    request.metadata.fields().forEach {
      spec = spec.and(documentSearchSpecification.metadataContains(it.key, it.value.asText()))
    }

    val results = documentRepository.findAll(spec)

    return DocumentSearchResult(
      request,
      results.toModels(),
    )
  }
}
