package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FilterOperator
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.MetadataFilter

@Component
class DocumentFacetSearchSqlBuilder {

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
      where += "duplicate_of IS NULL"
    }

    var paramIndex = 0
    filters.forEach { filter ->

      val param = "p$paramIndex"
      val field = "f$paramIndex"
      when (filter.operator) {
        FilterOperator.EQUALS -> {
          where += "metadata ->> :$field = :$param"
          params[param] = filter.value
        }

        FilterOperator.NOT_EQUALS -> {
          where += "metadata ->> :$field <> :$param"
          params[param] = filter.value
        }

        FilterOperator.IN -> {
          where += "metadata ->> :$field IN (:$param)"
          params[param] = filter.values
        }

        FilterOperator.EXISTS -> {
          where += "jsonb_exists(metadata, :$field)"
        }

        FilterOperator.NOT_EXISTS -> {
          where += "NOT jsonb_exists(metadata, :$field)"
        }

        FilterOperator.JSON_ARRAY_CONTAINS -> {
          val containsWhere = mutableListOf<String>()
          filter.values.forEach { value ->
            val innerParam = "p$paramIndex"
            containsWhere += "metadata -> :$field @> jsonb_build_array(:$innerParam)"
            params[innerParam] = value
            paramIndex += 1
          }
          where += "(" + containsWhere.joinToString(" OR ") + ")"
        }
      }
      params[field] = filter.field
      paramIndex += 1
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
}

class SqlWhere(
  val sql: String,
  val parameters: Map<String, Any>,
)
