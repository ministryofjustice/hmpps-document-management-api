package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchByUuidsRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentSearchedByUuidsEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_UUID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.TOTAL_RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.typeReference
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.String
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@TestPropertySource(
  properties = [
    "feature.hmpps.audit.enabled=true",
  ],
)
class DocumentSearchByUuidsIntTest : IntegrationTestBase() {
  @ParameterizedTest
  @MethodSource("documentSearchByUuidsAccessDeniedTestParameters")
  fun `test search by document UUIDs error when access is denied`(expectedStatus: Int, roles: List<String>?) = webTestClient.searchDocumentsAndAssertExpectedStatus(expectedStatus, roles)

  @ParameterizedTest
  @MethodSource("documentSearchByUuidsBadRequestTestParameters")
  fun `test search by document UUIDs error when it is a bad request`(expectedStatus: Int, setContext: Boolean, roles: List<String>, documentUuids: Collection<UUID>?, expectedUserMessage: String, expectedDeveloperMessage: String) {
    val response = webTestClient.searchDocumentsAndAssertExpectedStatus(expectedStatus, roles, documentUuids, setContext)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(status).isEqualTo(expectedStatus)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo(expectedUserMessage)
      assertThat(developerMessage).isEqualTo(expectedDeveloperMessage)
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `response contains search request`(documentUuids: Collection<UUID>, expectedResults: Int) {
    val expectedStatus = 200
    val roles = listOf(ROLE_DOCUMENT_READER)

    val response = webTestClient.searchDocumentsAndAssertExpectedStatus(expectedStatus, roles, documentUuids)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(typeReference<Collection<DocumentModel>>())
      .returnResult().responseBody!!

    with(response) {
      assertThat(size).isEqualTo(expectedResults)
      assertThat(filter { doc: DocumentModel -> documentUuids.contains(doc.documentUuid) }.size).isEqualTo(expectedResults)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `audits event`(documentUuids: Collection<UUID>, expectedResults: Int) {
    val expectedStatus = 200
    val roles = listOf(ROLE_DOCUMENT_READER)

    webTestClient.searchDocumentsAndAssertExpectedStatus(expectedStatus, roles, documentUuids)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(typeReference<Collection<DocumentModel>>())
      .returnResult().responseBody!!

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()

    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENTS_SEARCHED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), Assertions.within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(TEST_USERNAME)
      assertThat(service).isEqualTo(SERVICE_NAME)

      with(objectMapper.readValue<DocumentSearchedByUuidsEvent>(details)) {
        assertThat(request).isEqualTo(DocumentSearchByUuidsRequest(documentUuids))
        assertThat(resultsCount).isEqualTo(expectedResults)
      }
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `tracks event`(documentUuids: Collection<UUID>, expectedResults: Int) {
    val expectedStatus = 200
    val roles = listOf(ROLE_DOCUMENT_READER)

    webTestClient.searchDocumentsAndAssertExpectedStatus(expectedStatus, roles, documentUuids)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(typeReference<Collection<DocumentModel>>())
      .returnResult().responseBody!!

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENTS_SEARCHED.name), customEventProperties.capture(), customEventMetrics.capture())

    with(customEventProperties.firstValue) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(SERVICE_NAME)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(ACTIVE_CASE_LOAD_ID)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(TEST_USERNAME)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(documentUuids.joinToString { it.toString() })
    }

    with(customEventMetrics.firstValue) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
      assertThat(this[RESULTS_COUNT_METRIC_KEY]).isEqualTo(expectedResults.toDouble())
      assertThat(this[TOTAL_RESULTS_COUNT_METRIC_KEY]).isEqualTo(expectedResults.toDouble())
    }
  }

  private fun WebTestClient.searchDocumentsAndAssertExpectedStatus(
    expectedStatus: Int,
    roles: List<String>? = null,
    documentUuids: Collection<UUID>? = listOf(),
    setContext: Boolean = true,
  ): WebTestClient.ResponseSpec = post().uri(URI_SEARCH_BY_DOCUMENT_UUIDS).also {
    if (documentUuids != null) {
      it.bodyValue(DocumentSearchByUuidsRequest(documentUuids))
    }

    if (setContext) {
      it.headers(setDocumentContext(SERVICE_NAME, ACTIVE_CASE_LOAD_ID, TEST_USERNAME))
    }

    if (roles != null) {
      it.headers(setAuthorisation(roles = roles))
    }
  }.exchange().also {
    when (expectedStatus) {
      200 -> {
        it.expectStatus().isOk
      }
      401 -> {
        it.expectStatus().isUnauthorized
      }
      403 -> {
        it.expectStatus().isForbidden
      }
      400 -> {
        it.expectStatus().isBadRequest
      }
    }
  }

  companion object {
    const val URI_SEARCH_BY_DOCUMENT_UUIDS: String = "/documents/searchByUuids"
    const val SERVICE_NAME = "Searched using service name"
    const val ACTIVE_CASE_LOAD_ID = "KPI"
    const val TEST_USERNAME = "SEARCHED_BY_USERNAME"

    val MATCHING_DOCUMENT_UUID: UUID = UUID.fromString("443b5592-a87d-4b3d-8691-61daa7ec882f")
    val MATCHING_DOCUMENT_UUID_2: UUID = UUID.fromString("91211779-fccc-4e40-a7f5-27decf107df4")
    val NOT_MATCHING_DOCUMENT_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    @JvmStatic
    fun documentSearchByUuidsAccessDeniedTestParameters() = listOf(
      Arguments.of(401, null),
      Arguments.of(403, listOf<String>()),
      Arguments.of(403, listOf(ROLE_DOCUMENT_WRITER)),
    )

    @JvmStatic
    fun documentSearchByUuidsBadRequestTestParameters() = listOf(
      Arguments.of(400, false, listOf(ROLE_DOCUMENT_READER), listOf<UUID>(), "Exception: Service-Name header is required", "Service-Name header is required"),
      Arguments.of(400, true, listOf(ROLE_DOCUMENT_READER), null, "Validation failure: Couldn't read request body: Required request body is missing: public java.util.Collection<uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document> uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.searchByDocumentUuids(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchByUuidsRequest,jakarta.servlet.http.HttpServletRequest)", "Required request body is missing: public java.util.Collection<uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document> uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.searchByDocumentUuids(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchByUuidsRequest,jakarta.servlet.http.HttpServletRequest)"),
    )

    @JvmStatic
    fun documentSearchByUuidsTestParameters() = listOf(
      Arguments.of(listOf(NOT_MATCHING_DOCUMENT_UUID), 0),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID), 1),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID_2), 1),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID, NOT_MATCHING_DOCUMENT_UUID), 1),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID, MATCHING_DOCUMENT_UUID_2), 2),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID, MATCHING_DOCUMENT_UUID_2, NOT_MATCHING_DOCUMENT_UUID), 2),
    )
  }
}
