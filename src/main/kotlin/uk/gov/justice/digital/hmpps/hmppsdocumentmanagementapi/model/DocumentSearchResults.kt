package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema

data class DocumentSearchResults(
  @Schema(
    description = "Describes the search parameters that were used to filter documents",
  )
  val request: DocumentSearchRequest,

  @Schema(
    description = "The documents matching the supplied search parameters",
  )
  val results: Collection<Document>,
)