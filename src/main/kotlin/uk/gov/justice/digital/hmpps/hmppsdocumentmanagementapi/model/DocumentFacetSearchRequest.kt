package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Sort.Direction
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation.Between

@Schema(
  description = "Describes the search parameters to use to filter documents. Document type or metadata criteria " +
    "must be supplied.",
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

  val baseFilters: List<MetadataFilter>,
  val facetFilters: List<MetadataFilter>,
  val facets: List<FacetRequest>,

)

data class MetadataFilter(
  val field: String,
  val operator: FilterOperator = FilterOperator.EQUALS,
  val value: String,
)

enum class FilterOperator {
  EQUALS,
  NOT_EQUALS,
  IN,
  EXISTS,
  NOT_EXISTS,
}

data class FacetRequest(
  val field: String,
  val type: FacetType,
)

enum class FacetType {
  VALUE,
  ARRAY,
}
