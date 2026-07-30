package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.CaseReferencesService
import java.util.stream.Stream

class CaseReferencesIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var documentRepository: DocumentRepository

  @Autowired
  private lateinit var service: CaseReferencesService

  @Test
  fun `401 unauthorised`() {
    webTestClient.get()
      .uri("/court-documents/case-references/mockedPrisonerId")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.get()
      .uri("/court-documents/case-references/mockedPrisonerId")
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document writer`() {
    webTestClient.get()
      .uri("/court-documents/case-references/mockedPrisonerId")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @ParameterizedTest
  @CsvSource(
    "/court-documents/case-references",
    "/court-documents/case-references/",
    "/court-documents/case-references/ ",
  )
  fun `404 not found - when no prisonerId is provided, the endpoint is not resolved (found)`(uri: String) {
    val response = webTestClient.get()
      .uri(uri)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isNotFound
      .expectBody<ErrorResponse>()
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(404)
      assertThat(errorCode).isNull()
      assertThat(userMessage).contains("No resource found failure: No static resource court-documents/case-references for request")
      assertThat(developerMessage).startsWith("No static resource court-documents/case-references for request")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @ParameterizedTest
  @MethodSource("getCaseReferencesTestParameters")
  fun `response contains search request`(prisonerId: String, expected: List<String>) {
    val response = webTestClient.getCaseReferences(prisonerId)

    assertThat(response).hasSize(expected.size)
    assertThat(response).isEqualTo(expected)
  }

  private fun WebTestClient.getCaseReferences(
    prisonerId: String,
    roles: List<String> = listOf(ROLE_DOCUMENT_READER),
  ) = get()
    .uri("/court-documents/case-references/$prisonerId")
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(SERVICE_NAME, ACTIVE_CASE_LOAD_ID, TEST_USERNAME))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody<List<String>>()
    .returnResult().responseBody!!

  private companion object {
    const val SERVICE_NAME: String = "Searched using service name"
    const val ACTIVE_CASE_LOAD_ID: String = "KPI"
    const val TEST_USERNAME: String = "SEARCHED_BY_USERNAME"

    @JvmStatic
    fun getCaseReferencesTestParameters(): Stream<Arguments> = Stream.of(
      Arguments.of("A1234BC", listOf("CASE001", "CASE002", "CASE003")),
      Arguments.of("D4567EF", listOf("CASE001")),
      Arguments.of("E5678FG", listOf("CASE001")),
      Arguments.of("NO_EXISTS", emptyList<String>()),
    )
  }
}
