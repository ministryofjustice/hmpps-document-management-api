package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  description = "Describes the search parameters that were used to filter documents and the documents matching the supplied search parameters",
)
data class DocumentSearchResult(
  @Schema(
    description = "Describes the search parameters that were used to filter documents",
  )
  val request: DocumentSearchRequest,

  @Schema(
    description = "The documents matching the supplied search parameters. Note that documents with types that require " +
      "additional roles will have been filtered out of these results if the client does not have the required roles.",
  )
  val results: Collection<Document>,

  @Schema(
    description = "The total number of available results not limited by page size",
    example = "56",
  )
  val totalResults: Long,
)
