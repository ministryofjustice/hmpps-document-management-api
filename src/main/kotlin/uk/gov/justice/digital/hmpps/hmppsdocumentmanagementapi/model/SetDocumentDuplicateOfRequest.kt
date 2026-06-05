package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(
  description = "Sets, or clears, the canonical document a document is a duplicate of.",
)
data class SetDocumentDuplicateOfRequest(
  @Schema(
    description = "The unique identifier of the canonical document this document duplicates. Null makes this " +
      "document canonical.",
    example = "8cdadcf3-b003-4116-9956-c99bd8df6a00",
  )
  val duplicateOf: UUID?,
)
