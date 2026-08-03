package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchRequest

data class DocumentsFacetSearchedEvent(
  val request: DocumentFacetSearchRequest,
  val resultsCount: Int,
  val totalResultsCount: Long,
)
