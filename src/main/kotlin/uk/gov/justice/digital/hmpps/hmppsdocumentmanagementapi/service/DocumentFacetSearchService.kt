package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FilterOperator
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.MetadataFilter
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsFacetSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

@Service
class DocumentFacetSearchService(
  private val documentRepository: DocumentRepository,
  private val documentSearchSpecification: DocumentSearchSpecification,
  private val eventService: EventService,
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
  fun facetSearchDocuments(
    request: DocumentFacetSearchRequest,
    authorisedDocumentTypes: Collection<DocumentType>,
    documentRequestContext: DocumentRequestContext,
  ): DocumentFacetSearchResult {
    val startTimeInMs = System.currentTimeMillis()

    request.documentTypes.forEach { type ->
      if (!authorisedDocumentTypes.contains(type)) {
        throw AccessDeniedException("Document types '$type' require additional role")
      }
    }

    val baseWhere = buildWhere(request.documentTypes, request.canonical, request.baseFilters)

    val facetMapper = RowMapper<FacetValue> { rs, _ ->
      FacetValue(
        value = rs.getString("value"),
        count = rs.getLong("count"),
      )
    }

    val facets: Map<String, FacetResult> = request.facets.associate { facet ->
      val sql = when (facet.type) {
        FacetType.VALUE -> buildValueFacetQuery(facet.field, baseWhere)
        FacetType.ARRAY -> buildArrayFacetQuery(facet.field, baseWhere)
      }
      val values = jdbcTemplate.query(
        sql,
        MapSqlParameterSource(baseWhere.parameters),
        facetMapper,
      )
      facet.field to FacetResult(values)
    }

    val pageQueryWhere = buildWhere(request.documentTypes, request.canonical, (request.baseFilters + request.facetFilters))
    val pageSql = buildPageQuery(pageQueryWhere, request.orderBy.columnName, request.orderByDirection.name)

    val documentMapper = RowMapper<Long> { rs, _ ->
      rs.getLong("document_id")
    }
    val documentIds: List<Long> = jdbcTemplate.query(
      pageSql,
      MapSqlParameterSource(pageQueryWhere.parameters)
        .addValue("limit", request.pageSize)
        .addValue("offset", request.page * request.pageSize),
      documentMapper,
    )
    val countQuery = buildCountQuery(pageQueryWhere)
    val countMapper = RowMapper<Long> { rs, _ ->
      rs.getLong("count")
    }
    val totalResults = jdbcTemplate.query(
      countQuery,
      MapSqlParameterSource(pageQueryWhere.parameters),
      countMapper,
    )

    val documentsById = documentRepository.findAllById(documentIds)
      .associateBy { it.documentId }
    val documents = documentIds.mapNotNull(documentsById::get)
    return DocumentFacetSearchResult(
      request,
      documents.toModels(),
      totalResults[0],
      facets,
    ).also {
      eventService.recordDocumentsFacetSearchedEvent(
        DocumentsFacetSearchedEvent(it.request, it.results.size, it.totalResultsCount),
        documentRequestContext,
        System.currentTimeMillis() - startTimeInMs,
      )
    }
  }
}

class SqlWhere(
  val sql: String,
  val parameters: Map<String, Any>,
)

fun buildPageQuery(
  where: SqlWhere,
  orderColumn: String,
  orderDirection: String,
): String = """
        SELECT *
        FROM document
        WHERE ${where.sql}
        ORDER BY ${listOf(orderColumn, "created_time").distinct().joinToString(", ") { "$it $orderDirection" }}
        LIMIT :limit
        OFFSET :offset
""".trimIndent()

fun buildCountQuery(
  where: SqlWhere,
): String = """
        SELECT COUNT(*) as count
        FROM document
        WHERE ${where.sql}
""".trimIndent()
fun buildWhere(documentTypes: List<DocumentType>, canonical: Boolean?, filters: List<MetadataFilter>): SqlWhere {
  val where = mutableListOf<String>()
  val params = mutableMapOf<String, Any>()

  where += "deleted_time IS NULL"
  where += "document_type IN (:documentTypes)"
  params["documentTypes"] = documentTypes.map { it.name }

  canonical?.let {
    where += "duplicate_of IS NOT NULL"
  }

  filters.forEachIndexed { index, filter ->

    val param = "p$index"

    when (filter.operator) {
      FilterOperator.EQUALS -> {
        where += "metadata ->> '${filter.field}' = :$param"
        params[param] = filter.value!!
      }

      FilterOperator.NOT_EQUALS -> {
        where += "metadata ->> '${filter.field}' <> :$param"
        params[param] = filter.value!!
      }

      FilterOperator.IN -> {
        val values = filter.value!!.split(",").map { it }

        where += "metadata ->> '${filter.field}' IN (:$param)"
        params[param] = values
      }

      FilterOperator.EXISTS -> {
        where += "jsonb_exists(metadata, '${filter.field}')"
      }

      FilterOperator.NOT_EXISTS -> {
        where += "NOT jsonb_exists(metadata, '${filter.field}')"
      }
    }
  }

  return SqlWhere(
    sql = where.joinToString(" AND "),
    parameters = params,
  )
}

fun buildArrayFacetQuery(
  facet: String,
  where: SqlWhere,
): String = """
        SELECT
            tag.value,
            COUNT(*) AS count
        FROM document
        CROSS JOIN LATERAL (
            SELECT DISTINCT value
            FROM jsonb_array_elements_text(metadata -> '$facet') value
        ) tag
        WHERE ${where.sql}
        GROUP BY tag.value
        ORDER BY count DESC
""".trimIndent()

fun buildValueFacetQuery(
  facet: String,
  where: SqlWhere,
): String = """
        SELECT
            metadata ->> '$facet' AS value,
            COUNT(*) AS count
        FROM document
        WHERE ${where.sql}
        GROUP BY value
        ORDER BY count DESC
""".trimIndent()
