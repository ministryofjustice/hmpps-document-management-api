package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.TestConstants
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.assertIsDocumentWithNoMetadataHistoryId1
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_UUID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.FILE_EXTENSION_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.FILE_SIZE_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.METADATA_FIELD_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.MIME_TYPE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ORIGINAL_METADATA_FIELD_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@TestPropertySource(
  properties = [
    "feature.hmpps.audit.enabled=true",
  ],
)
class ReplaceDocumentMetadataIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"B2345CD\" }")
  private val serviceName = "Replaced metadata using service name"
  private val activeCaseLoadId = "MDI"
  private val username = "REPLACED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document reader`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
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
      .uri("/documents/${TestConstants.INVALID_UUID}/metadata")
      .bodyValue(metadata)
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
  fun `400 bad request - no body`() {
    val response = webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Couldn't read request body: Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.replaceDocumentMetadata(java.util.UUID,com.fasterxml.jackson.databind.JsonNode,jakarta.servlet.http.HttpServletRequest)")
      assertThat(developerMessage).isEqualTo("Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.replaceDocumentMetadata(java.util.UUID,com.fasterxml.jackson.databind.JsonNode,jakarta.servlet.http.HttpServletRequest)")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `404 not found`() {
    val response = webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
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

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `response contains replaced metadata`() {
    val response = webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    assertThat(response.metadata).isEqualTo(metadata)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `document contains replaced metadata`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.metadata).isEqualTo(metadata)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `metadata history contains original metadata`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.documentMetadataHistory().single().metadata)
      .isEqualTo(JacksonUtil.toJsonNode("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"))
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `metadata history uses document context`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    with(entity.documentMetadataHistory().single()) {
      assertThat(supersededTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(supersededByServiceName).isEqualTo(serviceName)
      assertThat(supersededByUsername).isEqualTo(username)
    }
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `audits event`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENT_METADATA_REPLACED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      with(objectMapper.readValue<DocumentMetadataReplacedEvent>(details)) {
        document.assertIsDocumentWithNoMetadataHistoryId1(metadata)
        assertThat(originalMetadata)
          .isEqualTo(JacksonUtil.toJsonNode("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"))
      }
    }
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `tracks event`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENT_METADATA_REPLACED.name), customEventProperties.capture(), customEventMetrics.capture())

    with(customEventProperties.firstValue) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo("f73a0f91-2957-4224-b477-714370c04d37")
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(DocumentType.HMCTS_WARRANT.name)
      assertThat(this[DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY]).isEqualTo(DocumentType.HMCTS_WARRANT.description)
      assertThat(this[FILE_EXTENSION_PROPERTY_KEY]).isEqualTo("pdf")
      assertThat(this[MIME_TYPE_PROPERTY_KEY]).isEqualTo("application/pdf")
    }

    with(customEventMetrics.firstValue) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(20688.0)
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(1.0)
      assertThat(this[ORIGINAL_METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(2.0)
    }
  }

  private fun WebTestClient.replaceDocumentMetadata(
    documentUuid: UUID,
    metadata: JsonNode,
  ) = put()
    .uri("/documents/$documentUuid/metadata")
    .bodyValue(metadata)
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(DocumentModel::class.java)
    .returnResult().responseBody!!
}
