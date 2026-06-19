package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(
  description = "Describes the search parameters to use to filter documents. Document type or metadata criteria " +
    "must be supplied.",
)
data class DocumentFindByUuidsRequest(
  @Schema(
    description = "The list of document Uuid to search for",
    example = "4fd5f7b0-eebf-4b69-9489-0cc48550e03b, 6ft3h6a1-ksfa-3j61-2583-0cc48550e57z",
    required = true,
  )
  val documentUuids: Collection<UUID>,
)
