package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.jpa.domain.Specification
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository

class CaseReferencesServiceTest {
  private val documentRepository: DocumentRepository = mock()

  private val service = CaseReferencesService(documentRepository)

  @Test
  fun `test getCaseReferences request parameters, should return valid interaction with search specification`() {
    whenever(documentRepository.findAll(any<Specification<Document>>())).thenReturn(emptyList())

    service.getCaseReferences(PRISONER_ID_VALUE)

    verify(documentRepository).findCaseReferences(any<String>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `when no documents found, then returns empty list`() {
    whenever(documentRepository.findCaseReferences(any<String>()))
      .thenReturn(listOf())

    val response = service.getCaseReferences(PRISONER_ID_VALUE)

    assertThat(response).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("getCaseReferencesTestParameters")
  fun `returns results`(metadata: JsonNode, expected: Set<String>) {
    whenever(documentRepository.findCaseReferences(any<String>()))
      .thenReturn(listOf(metadata.get("caseReferences").toString()))

    val response = service.getCaseReferences(PRISONER_ID_VALUE)

    assertThat(response).isEqualTo(expected)
  }

  private companion object {
    const val PRISONER_ID_KEY: String = "prisonerId"
    const val PRISONER_ID_VALUE: String = "A1234BC"

    val METADATA_CASE_REFERENCE_1: JsonNode = ObjectMapper().readTree("{ \"${PRISONER_ID_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\", \"caseReferences\": [\"CASE-REF-1\"] }")
    val METADATA_CASE_REFERENCE_2: JsonNode = ObjectMapper().readTree("{ \"${PRISONER_ID_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\", \"caseReferences\": [\"CASE-REF-1\", \"CASE-REF-2\"] }")
    val METADATA_CASE_REFERENCE_0: JsonNode = ObjectMapper().readTree("{ \"${PRISONER_ID_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\", \"caseReferences\": [] }")

    @JvmStatic
    fun getCaseReferencesTestParameters() = listOf(
      Arguments.of(METADATA_CASE_REFERENCE_1, parseMetadataCaseReferences(METADATA_CASE_REFERENCE_1)),
      Arguments.of(METADATA_CASE_REFERENCE_2, parseMetadataCaseReferences(METADATA_CASE_REFERENCE_2)),
      Arguments.of(METADATA_CASE_REFERENCE_0, emptySet<String>()),
    )

    fun parseMetadataCaseReferences(metadata: JsonNode) = ObjectMapper()
      .readValue(
        metadata.path("caseReferences").toString(),
        Array<String>::class.java,
      ).toSet()
  }
}
