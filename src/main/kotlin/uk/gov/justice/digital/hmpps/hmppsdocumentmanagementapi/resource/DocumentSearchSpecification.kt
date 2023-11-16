package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

@Component
class DocumentSearchSpecification {
  fun prisonCodeEquals(documentType: DocumentType) =
    Specification<Document> { root, _, cb -> cb.equal(root.get<String>("documentType"), documentType) }
}
