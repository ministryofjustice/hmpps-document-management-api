package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

@Component
class DocumentSearchSpecification {
  fun documentTypeIn(documentTypes: Collection<DocumentType>) =
    Specification<Document> { root, _, _ -> root.get<String>("documentType").`in`(documentTypes) }

  fun metadataContains(property: String, value: String) =
    Specification<Document> { root, _, cb ->
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
}
