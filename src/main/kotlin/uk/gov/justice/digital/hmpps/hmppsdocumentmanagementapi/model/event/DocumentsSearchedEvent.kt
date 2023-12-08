package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest

data class DocumentsSearchedEvent(
  val request: DocumentSearchRequest,
  val resultsCount: Int,
)
