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
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.MetadataFilter
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsFacetSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository

@Service
class DocumentFacetSearchService(
  private val documentRepository: DocumentRepository,
  private val searchSqlBuilder: DocumentFacetSearchSqlBuilder,
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

    val facetMapper = RowMapper<FacetValue> { rs, _ ->
      FacetValue(
        value = rs.getString("value"),
        count = rs.getLong("count"),
      )
    }

    val facets: Map<String, FacetResult> = request.facets.associate { facet ->
      val filters: List<MetadataFilter> = facet.filters?.let { facet.filters }.orEmpty()
      val baseWhere = searchSqlBuilder.buildWhere(request.documentTypes, request.canonical, filters)
      val sql = when (facet.type) {
        FacetType.VALUE -> searchSqlBuilder.buildValueFacetQuery(facet.field, baseWhere)
        FacetType.ARRAY -> searchSqlBuilder.buildArrayFacetQuery(facet.field, baseWhere)
      }
      val values = jdbcTemplate.query(
        sql,
        MapSqlParameterSource(baseWhere.parameters),
        facetMapper,
      )
      facet.field to FacetResult(values)
    }

    val pageQueryWhere = searchSqlBuilder.buildWhere(request.documentTypes, request.canonical, request.metadataFilters)
    val pageSql = searchSqlBuilder.buildPageQuery(pageQueryWhere, request.orderBy.columnName, request.orderByDirection.name)

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
    val countQuery = searchSqlBuilder.buildCountQuery(pageQueryWhere)
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
