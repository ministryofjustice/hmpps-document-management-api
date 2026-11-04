package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
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
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_UUID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.FILE_SIZE_METRIC_KEY
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
class PurgeDocumentIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  @Autowired
  lateinit var jdbcTemplate: JdbcTemplate

  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val documentWithHistoryUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6a00")
  private val softDeletedDocumentUuid = UUID.fromString("c671814d-7c32-4394-b46d-a70957148925")
  private val serviceName = "Purged using service name"
  private val activeCaseLoadId = "RSI"
  private val username = "PURGED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}/purge")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}/purge")
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document writer is not enough`() {
    webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}/purge")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}/purge")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_ADMIN)))
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
    val response = webTestClient.delete()
      .uri("/documents/${TestConstants.INVALID_UUID}/purge")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_ADMIN)))
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
  fun `204 no content when document unique identifier not found`() {
    webTestClient.purgeDocument(UUID.randomUUID())
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `removes the document row and the stored file`() {
    putDocumentInS3(documentUuid, "test_data/warrant-for-remand.pdf", S3BucketName.DOCUMENT_MANAGEMENT.value)

    webTestClient.purgeDocument(documentUuid)

    assertThat(repository.findByDocumentUuidIncludingSoftDeleted(documentUuid)).isNull()
    assertFileNotInS3(documentUuid)
  }

  @Sql("classpath:test_data/document-with-metadata-history-id-2.sql")
  @Test
  fun `removes metadata history with the document`() {
    putDocumentInS3(documentWithHistoryUuid, "test_data/warrant-for-remand.pdf", S3BucketName.DOCUMENT_MANAGEMENT.value)
    assertThat(metadataHistoryCount(2)).isGreaterThan(0)

    webTestClient.purgeDocument(documentWithHistoryUuid)

    assertThat(repository.findByDocumentUuidIncludingSoftDeleted(documentWithHistoryUuid)).isNull()
    assertThat(metadataHistoryCount(2)).isZero()
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `purges a document that has already been soft deleted`() {
    putDocumentInS3(softDeletedDocumentUuid, "test_data/warrant-for-remand.pdf", S3BucketName.DOCUMENT_MANAGEMENT.value)

    webTestClient.purgeDocument(softDeletedDocumentUuid)

    assertThat(repository.findByDocumentUuidIncludingSoftDeleted(softDeletedDocumentUuid)).isNull()
    assertFileNotInS3(softDeletedDocumentUuid)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `purge succeeds when the stored file is already missing`() {
    webTestClient.purgeDocument(documentUuid)

    assertThat(repository.findByDocumentUuidIncludingSoftDeleted(documentUuid)).isNull()
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `audits event`() {
    webTestClient.purgeDocument(documentUuid)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENT_PURGED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      objectMapper.readValue<Document>(details).assertIsDocumentWithNoMetadataHistoryId1()
    }
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `tracks event`() {
    webTestClient.purgeDocument(documentUuid)

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENT_PURGED.name), customEventProperties.capture(), customEventMetrics.capture())

    with(customEventProperties.firstValue) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(documentUuid.toString())
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(DocumentType.HMCTS_WARRANT.name)
    }

    with(customEventMetrics.firstValue) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(20688.0)
    }
  }

  private fun metadataHistoryCount(documentId: Long): Int = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM document_metadata_history WHERE document_id = ?",
    Int::class.javaObjectType,
    documentId,
  )!!

  private fun assertFileNotInS3(documentUuid: UUID) {
    val request = HeadObjectRequest.builder()
      .bucket(bucketName(S3BucketName.DOCUMENT_MANAGEMENT.value))
      .key(documentUuid.toString())
      .build()
    assertThatThrownBy { s3Client.headObject(request) }.isInstanceOf(NoSuchKeyException::class.java)
  }

  private fun WebTestClient.purgeDocument(documentUuid: UUID) = delete()
    .uri("/documents/$documentUuid/purge")
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_ADMIN, ROLE_DOCUMENT_TYPE_SAR)))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isNoContent
}
