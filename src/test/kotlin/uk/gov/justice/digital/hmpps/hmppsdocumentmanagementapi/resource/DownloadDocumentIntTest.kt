package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.S3BucketName
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.TestConstants
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.assertIsDocumentWithNoMetadataHistoryId1
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
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
class DownloadDocumentIntTest : IntegrationTestBase() {
  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val serviceName = "Uploaded via service name"
  private val activeCaseLoadId = "PVI"
  private val username = "UPLOADED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.get()
      .uri("/documents/${UUID.randomUUID()}/file")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.get()
      .uri("/documents/${UUID.randomUUID()}/file")
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document writer`() {
    webTestClient.get()
      .uri("/documents/${UUID.randomUUID()}/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.get()
      .uri("/documents/${UUID.randomUUID()}/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
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
    val response = webTestClient.get()
      .uri("/documents/${TestConstants.INVALID_UUID}/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
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
  fun `404 document not found`() {
    val response = webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
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
  fun `404 file not found`() {
    val response = webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isNotFound
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(404)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Not found: Document file with UUID '$documentUuid' not found.")
      assertThat(developerMessage).isEqualTo("Document file with UUID '$documentUuid' not found.")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `download document success`() {
    val fileBytes = putDocumentInS3(documentUuid, "test_data/warrant-for-remand.pdf", S3BucketName.DOCUMENT_MANAGEMENT.value)

    val response = webTestClient.downloadDocument(
      documentUuid,
      MediaType.APPLICATION_PDF,
      20688,
      "warrant_for_remand.pdf",
    )

    assertThat(response.responseBody).isEqualTo(fileBytes)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `audits event`() {
    putDocumentInS3(documentUuid, "test_data/warrant-for-remand.pdf", S3BucketName.DOCUMENT_MANAGEMENT.value)

    webTestClient.downloadDocument(
      documentUuid,
      MediaType.APPLICATION_PDF,
      20688,
      "warrant_for_remand.pdf",
    )

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENT_FILE_DOWNLOADED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), Assertions.within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      objectMapper.readValue<Document>(details).assertIsDocumentWithNoMetadataHistoryId1()
    }
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `tracks event`() {
    putDocumentInS3(documentUuid, "test_data/warrant-for-remand.pdf", S3BucketName.DOCUMENT_MANAGEMENT.value)

    webTestClient.downloadDocument(
      documentUuid,
      MediaType.APPLICATION_PDF,
      20688,
      "warrant_for_remand.pdf",
    )

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENT_FILE_DOWNLOADED.name), customEventProperties.capture(), customEventMetrics.capture())

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
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(2.0)
    }
  }

  private fun WebTestClient.downloadDocument(
    documentUuid: UUID,
    contentType: MediaType,
    contentLength: Long,
    filename: String,
  ) = get()
    .uri("/documents/$documentUuid/file")
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(contentType)
    .expectHeader().contentLength(contentLength)
    .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"$filename\""))
    .expectBody(ByteArray::class.java)
    .returnResult()
}
