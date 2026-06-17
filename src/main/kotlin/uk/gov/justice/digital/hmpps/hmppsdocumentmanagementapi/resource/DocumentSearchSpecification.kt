package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.util.UUID

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

  fun documentUuidIn(documentIds: Collection<UUID>) = Specification<Document> { root, _, _ -> root.get<UUID>("documentUuid").`in`(documentIds) }
}
