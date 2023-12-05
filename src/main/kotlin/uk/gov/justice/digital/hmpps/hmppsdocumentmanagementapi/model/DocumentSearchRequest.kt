package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.DocumentTypeOrMetadataCriteriaRequired
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.NoNullOrEmptyStringMetadataValues

@Schema(
  description = "Describes the search parameters to use to filter documents. Document type or metadata criteria " +
    "must be supplied.",
)
@DocumentTypeOrMetadataCriteriaRequired
data class DocumentSearchRequest(
  @Schema(
    description = "The type or category of the document within HMPPS",
    example = "HMCTS_WARRANT",
  )
  val documentType: DocumentType?,

  @Schema(
    description = "JSON structured metadata to match with document metadata. Documents will match if their metadata " +
      "contains all the supplied properties and their values e.g. prisonCode = \"KMI\" AND prisonNumber = \"A1234BC\". " +
      "Value matching is partial and case insensitive so court = \"ham magis\" will match \"Birmingham Magistrates\". " +
      "Property values must be strings and cannot be null or empty.",
    example =
    """
    {
      "prisonCode": "KMI",
      "prisonNumber": "C3456DE"
    }
    """,
  )
  @field:NoNullOrEmptyStringMetadataValues
  val metadata: JsonNode?,
)
