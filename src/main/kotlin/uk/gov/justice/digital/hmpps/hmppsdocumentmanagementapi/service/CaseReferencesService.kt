package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository

@Service
class CaseReferencesService(
  private val documentRepository: DocumentRepository,
) {
  fun getCaseReferences(prisonerId: String): Set<String> = convertCaseReferences(
    documentRepository.findCaseReferences(prisonerId),
  )

  private fun convertCaseReferences(caseReferences: List<String>) = caseReferences
    .asSequence().map { convertCaseReference(it) }.flatMap { it.asSequence() }.toSet()

  private fun convertCaseReference(caseReference: String) = ObjectMapper()
    .readValue(caseReference, Array<String>::class.java).toSet()
}
