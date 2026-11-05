package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.TestConstants
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.assertIsDocumentWithNoMetadataHistoryId1
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_UUID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature.hmpps.audit.enabled=true",
  ],
)
class ReactivateDocumentIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  private val activeDocumentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val softDeletedDocumentUuid = UUID.fromString("c671814d-7c32-4394-b46d-a70957148925")
  private val serviceName = "Reactivated using service name"
  private val activeCaseLoadId = "RSI"
  private val username = "REACTIVATED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.put()
      .uri("/documents/${UUID.randomUUID()}/reactivate")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.put()
      .uri("/documents/${UUID.randomUUID()}/reactivate")
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document reader`() {
    webTestClient.put()
      .uri("/documents/${UUID.randomUUID()}/reactivate")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.put()
      .uri("/documents/${UUID.randomUUID()}/reactivate")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Exception: Service-Name header is required")
      assertThat(developerMessage).isEqualTo("Service-Name header is required")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - invalid document uuid`() {
    val response = webTestClient.put()
      .uri("/documents/${TestConstants.INVALID_UUID}/reactivate")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Parameter documentUuid must be of type java.util.UUID")
      assertThat(developerMessage).isEqualTo(String.format(TestConstants.INVALID_UUID_EXCEPTION_MESSAGE_TEMPLATE, TestConstants.INVALID_UUID))
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `404 not found when document unique identifier not found`() {
    val documentUuid = UUID.randomUUID()

    val response = webTestClient.put()
      .uri("/documents/$documentUuid/reactivate")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isNotFound
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(404)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Not found: Document with UUID '$documentUuid' not found.")
      assertThat(developerMessage).isEqualTo("Document with UUID '$documentUuid' not found.")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `clears the deleted columns and returns the document`() {
    val response = webTestClient.reactivateDocument(softDeletedDocumentUuid)

    assertThat(response.documentUuid).isEqualTo(softDeletedDocumentUuid)
    assertThat(response.documentType).isEqualTo(DocumentType.HMCTS_WARRANT)

    val entity = repository.findByDocumentUuidIncludingSoftDeleted(softDeletedDocumentUuid)!!
    with(entity) {
      assertThat(deletedTime).isNull()
      assertThat(deletedByServiceName).isNull()
      assertThat(deletedByUsername).isNull()
    }
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `reactivated document is visible to retrieval again`() {
    webTestClient.reactivateDocument(softDeletedDocumentUuid)

    webTestClient.get()
      .uri("/documents/$softDeletedDocumentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isOk
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `document that is not deleted is returned unchanged without an event`() {
    val response = webTestClient.reactivateDocument(activeDocumentUuid)

    response.assertIsDocumentWithNoMetadataHistoryId1()
    verify(telemetryClient, never()).trackEvent(eq(EventType.DOCUMENT_REACTIVATED.name), any(), any())
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `audits event`() {
    webTestClient.reactivateDocument(softDeletedDocumentUuid)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENT_REACTIVATED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      assertThat(objectMapper.readValue<Document>(details).documentUuid).isEqualTo(softDeletedDocumentUuid)
    }
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `tracks event`() {
    webTestClient.reactivateDocument(softDeletedDocumentUuid)

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENT_REACTIVATED.name), customEventProperties.capture(), customEventMetrics.capture())

    with(customEventProperties.firstValue) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(softDeletedDocumentUuid.toString())
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(DocumentType.HMCTS_WARRANT.name)
    }

    with(customEventMetrics.firstValue) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
    }
  }

  private fun WebTestClient.reactivateDocument(documentUuid: UUID) = put()
    .uri("/documents/$documentUuid/reactivate")
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_SAR)))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectBody(Document::class.java)
    .returnResult().responseBody!!
}
