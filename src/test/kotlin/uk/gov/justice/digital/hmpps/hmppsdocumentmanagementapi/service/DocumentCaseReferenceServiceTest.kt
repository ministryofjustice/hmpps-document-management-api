package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.jpa.domain.Specification
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

class DocumentCaseReferenceServiceTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentSearchSpecification: DocumentSearchSpecification = spy()

  private val service = DocumentCaseReferenceService(documentRepository, documentSearchSpecification)

  @Test
  fun `test getCaseReferences request parameters, should return valid interaction with search specification`() {
    whenever(documentRepository.findAll(any<Specification<Document>>())).thenReturn(emptyList())

    service.getCaseReferences(PRISONER_ID_VALUE, DocumentType.entries)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verify(documentSearchSpecification).metadataEquals(PRISONER_ID_KEY, PRISONER_ID_VALUE)
    verify(documentSearchSpecification).metadataEquals("status", "ACTIVE")
    verify(documentSearchSpecification).canonical(true)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `when no documents found, then returns empty list`() {
    whenever(documentRepository.findAll(any<Specification<Document>>()))
      .thenReturn(listOf())

    val response = service.getCaseReferences(PRISONER_ID_VALUE, DocumentType.entries)

    assertThat(response).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("getCaseReferencesTestParameters")
  fun `returns results`(metadata: JsonNode, expected: Set<String>) {
    whenever(documentRepository.findAll(any<Specification<Document>>()))
      .thenReturn(listOf(buildMockedDocument(metadata)))

    val response = service.getCaseReferences(PRISONER_ID_VALUE, DocumentType.entries)

    assertThat(response).isEqualTo(expected)
  }

  private companion object {
    const val PRISONER_ID_KEY: String = "prisonerId"
    const val PRISONER_ID_VALUE: String = "A1234BC"
    const val PRISON_NUMBER_KEY: String = "prisonNumber"

    val METADATA_CASE_REFERENCE_1: JsonNode = ObjectMapper().readTree("{ \"${PRISON_NUMBER_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\", \"caseReferences\": [\"CASE-REF-1\"] }")
    val METADATA_CASE_REFERENCE_2: JsonNode = ObjectMapper().readTree("{ \"${PRISON_NUMBER_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\", \"caseReferences\": [\"CASE-REF-1\", \"CASE-REF-2\"] }")
    val METADATA_CASE_REFERENCE_0: JsonNode = ObjectMapper().readTree("{ \"${PRISON_NUMBER_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\", \"caseReferences\": [] }")
    val METADATA_CASE_REFERENCE_NULL: JsonNode = ObjectMapper().readTree("{ \"${PRISON_NUMBER_KEY}\": \"${PRISONER_ID_VALUE}\", \"status\": \"ACTIVE\"}")

    @JvmStatic
    fun buildMockedDocument(metadata: JsonNode) = Document(
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant_for_remand",
      fileExtension = "pdf",
      fileSize = 48243,
      fileHash = "d58e3582afa99040e27b92b13c8f2280",
      mimeType = "application/pdf",
      metadata = metadata,
      createdByServiceName = "Remand and Sentencing",
      createdByUsername = "CREATED_BY_USER",
    )

    @JvmStatic
    fun getCaseReferencesTestParameters() = listOf(
      Arguments.of(METADATA_CASE_REFERENCE_1, parseMetadataCaseReferences(METADATA_CASE_REFERENCE_1)),
      Arguments.of(METADATA_CASE_REFERENCE_2, parseMetadataCaseReferences(METADATA_CASE_REFERENCE_2)),
      Arguments.of(METADATA_CASE_REFERENCE_0, emptySet<String>()),
      Arguments.of(METADATA_CASE_REFERENCE_NULL, emptySet<String>()),
    )

    fun parseMetadataCaseReferences(metadata: JsonNode) = ObjectMapper()
      .readValue(
        metadata.path("caseReferences").toString(),
        Array<String>::class.java,
      ).toSet()
  }
}
