package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

@Service
class DocumentCaseReferenceService(
  private val documentRepository: DocumentRepository,
  private val documentSearchSpecification: DocumentSearchSpecification,
) {
  fun getCaseReferences(
    prisonerId: String,
    authorisedDocumentTypes: Collection<DocumentType>,
  ): Set<String> {
    var spec = documentSearchSpecification.documentTypeIn(authorisedDocumentTypes)
    spec = spec.and(documentSearchSpecification.metadataEquals("prisonerId", prisonerId))
    spec = spec.and(documentSearchSpecification.metadataEquals("status", "ACTIVE"))
    spec = spec.and(documentSearchSpecification.canonical(true))

    val documents = documentRepository.findAll(spec)

    return documents
      .asSequence()
      .mapNotNull { it.metadata.get("caseReferences") }
      .filter { it.isArray }
      .flatMap { it.asSequence() }
      .map { it.asString() }
      .toSet()
  }
}
