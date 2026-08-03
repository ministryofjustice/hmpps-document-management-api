package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import jakarta.persistence.criteria.Expression
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FilterOperator
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.MetadataFilter

@Component
class DocumentSearchSpecification {
  fun documentTypeIn(documentTypes: Collection<DocumentType>) = Specification<Document> { root, _, _ -> root.get<String>("documentType").`in`(documentTypes) }

  fun metadataContains(property: String, value: String) = Specification<Document> { root, _, cb ->
    cb.like(
      cb.function(
        "lower",
        String::class.java,
        cb.function(
          "jsonb_extract_path_text",
          String::class.java,
          root.get<String>("metadata"),
          cb.literal(property),
        ),
      ),
      "%${value.lowercase()}%",
    )
  }

  fun fileContentHashEquals(hash: String) = Specification<Document> { root, _, cb ->
    cb.equal(root.get<String>("fileContentHash"), hash.lowercase())
  }

  fun fileHashEquals(hash: String) = Specification<Document> { root, _, cb ->
    cb.equal(root.get<String>("fileHash"), hash.lowercase())
  }

  fun canonical(isCanonical: Boolean) = Specification<Document> { root, _, cb ->
    if (isCanonical) cb.isNull(root.get<Any>("duplicateOf")) else cb.isNotNull(root.get<Any>("duplicateOf"))
  }

  fun metadataEquals(property: String, value: String) = Specification<Document> { root, _, cb ->
    cb.equal(
      cb.function(
        "lower",
        String::class.java,
        cb.function(
          "jsonb_extract_path_text",
          String::class.java,
          root.get<String>("metadata"),
          cb.literal(property),
        ),
      ),
      value.lowercase(),
    )
  }

  fun metadataArrayContains(property: String, value: String) = Specification<Document> { root, cq, cb ->
    cb.literal(value.lowercase()).`in`(
      cq.subquery(Expression::class.java).select(
        cb.function(
          "lower",
          String::class.java,
          cb.function(
            "jsonb_array_elements_text",
            String::class.java,
            cb.function(
              "jsonb_extract_path",
              Any::class.java,
              root.get<String>("metadata"),
              cb.literal(property),
            ),
          ),
        ) as Expression<Expression<*>?>?,
      ),
    )
  }

  fun metaDataFilter(filter: MetadataFilter): Specification<Document> = Specification { root, _, cb ->

    val metadataValue = cb.function(
      "jsonb_extract_path_text",
      String::class.java,
      root.get<String>("metadata"),
      cb.literal(filter.field),
    )

    when (filter.operator) {
      FilterOperator.EQUALS ->
        cb.equal(metadataValue, filter.value)

      FilterOperator.NOT_EQUALS ->
        cb.notEqual(metadataValue, filter.value)

      FilterOperator.IN ->
        metadataValue.`in`(
          filter.value!!.split(",").map { it },
        )
      FilterOperator.EXISTS -> {
        cb.isNotNull(metadataValue)
      }
      FilterOperator.NOT_EXISTS -> {
        cb.isNull(metadataValue)
      }
    }
  }
}
