package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Sort.Direction
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.Between

@Schema(
  description = "Describes the search parameters to use to filter documents along with a list of facets available for additional filtering.",
)
data class DocumentFacetSearchRequest(
  @Schema(
    description = "The types or categories of the document within HMPPS",
    example = "[HMCTS_WARRANT]",
  )
  val documentTypes: List<DocumentType>,

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
    maximum = "200",
  )
  @field:Between(min = 1, max = 200, message = "Page size must be between 1 and 200.")
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
    description = "Filter by canonical status. True returns only canonical documents (those that are not a duplicate " +
      "of another), false returns only duplicates. Low selectivity on its own, so it refines a query rather than " +
      "standing alone and must be combined with document types or metadata.",
    example = "true",
  )
  val canonical: Boolean? = null,

  @Schema(
    description = "Filters that will be applied to the entire dataset, regardless of whicih facets are requested/filtered. Will also apply to the count of each facet",
  )
  val baseFilters: List<MetadataFilter> = emptyList(),

  @Schema(
    description = "Filters that will apply to the paged resultset and not to the faceted counts.",
  )
  val facetFilters: List<MetadataFilter> = emptyList(),

  @Schema(
    description = "A list of available searchable facets.",
  )
  val facets: List<FacetRequest> = emptyList(),

)

data class MetadataFilter(
  @Schema(
    description = "The metadata field to filter on",
    example = "status",
  )
  val field: String,

  @Schema(
    description = "The filter operation",
    example = "EQUALS",
  )
  val operator: FilterOperator = FilterOperator.EQUALS,

  @Schema(
    description = "The value to filter for. Not required for EXISTS/NOT_EXISTS filters",
    example = "ACTIVE",
  )
  val value: String?,
)

enum class FilterOperator {
  EQUALS,
  NOT_EQUALS,
  IN,
  EXISTS,
  NOT_EXISTS,
}

data class FacetRequest(
  @Schema(
    description = "The metadata field that will be a facet in the search UI",
    example = "ACTIVE",
  )
  val field: String,
  @Schema(
    description = "Is the metadata field an array or plain string",
    example = "ACTIVE",
  )
  val type: FacetType,
)

enum class FacetType {
  VALUE,
  ARRAY,
}
