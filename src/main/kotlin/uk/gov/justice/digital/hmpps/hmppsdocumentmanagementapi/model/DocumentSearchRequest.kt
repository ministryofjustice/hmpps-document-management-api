package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

@Schema(
  description = "Describes the search parameters to use to filter documents.",
)
data class DocumentSearchRequest(
  @Schema(
    description = "The type or category of the document within HMPPS (optional)",
    example = "HMCTS_WARRANT",
  )
  val documentType: DocumentType?,

  @field:NotEmpty(message = "Document metadata must be supplied")
  @Schema(
    description = "JSON structured metadata to match with document metadata. Documents will match if their metadata " +
      "contains all the supplied properties and their values e.g. prisonCode = \"KMI\" AND prisonNumber = \"A1234BC\". " +
      "Value matching is case insensitive so court = \"birmingham magistrates\" will match \"Birmingham Magistrates\".",
    example =
    """
    {
      "prisonCode": "KMI",
      "prisonNumber": "C3456DE"
    }
    """,
  )
  val metadata: JsonNode,
)
