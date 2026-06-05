package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Sort.Direction
import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.Between
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.DocumentTypeOrMetadataCriteriaRequired
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.NoNullOrEmptyStringMetadataValues

@Schema(
  description = "Describes the search parameters to use to filter documents. Document type or metadata criteria " +
    "must be supplied.",
)
@DocumentTypeOrMetadataCriteriaRequired
data class DocumentSearchRequest(
  @Schema(
    description = "The types or categories of the document within HMPPS",
    example = "[HMCTS_WARRANT]",
  )
  val documentTypes: List<DocumentType>?,

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

  @Schema(
    description = "The requested page of search results. Starts from 0",
    example = "5",
    defaultValue = "0",
  )
  @field:Min(0, message = "Page must be 0 or greater.")
  val page: Int = 0,

  @Schema(
    description = "The number of results to return per page",
    example = "25",
    defaultValue = "10",
    minimum = "1",
    maximum = "100",
  )
  @field:Between(min = 1, max = 100, message = "Page size must be between 1 and 100.")
  val pageSize: Int = 10,

  @Schema(
    description = "The property to order the search results by",
    example = "FILESIZE",
    defaultValue = "CREATED_TIME",
  )
  val orderBy: DocumentSearchOrderBy = DocumentSearchOrderBy.CREATED_TIME,

  @Schema(
    description = "The sort direction to use when ordering search results",
    example = "ASC",
    defaultValue = "DESC",
  )
  val orderByDirection: Direction = Direction.DESC,

  @Schema(
    description = "Exact match on the SHA-256 of the document's extracted content, as lowercase hex. Unlike metadata " +
      "matching this is an exact, case insensitive equality check, intended for finding documents that share the same " +
      "extracted content. Combine with documentTypes to keep the match scoped to a single source's documents.",
    example = "58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae",
  )
  val fileContentHash: String? = null,

  @Schema(
    description = "Exact match on the SHA-256 of the stored file bytes, as lowercase hex. An exact, case insensitive " +
      "equality check intended for finding byte-identical copies of a file. Combine with documentTypes to keep the " +
      "match scoped to a single source's documents.",
    example = "fffac8f1a93fabc8ad1629d255527c6ae12abfc5cc0921def588bfa2ce00b024",
  )
  val fileHash: String? = null,
)
