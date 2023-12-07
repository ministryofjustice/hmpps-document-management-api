package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.assertIsDocumentWithNoMetadataHistoryId1
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class DownloadDocumentIntTest : IntegrationTestBase() {
  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val serviceName = "Uploaded via service name"
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
      .uri("/documents/INVALID/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Parameter documentUuid must be of type java.util.UUID")
      assertThat(developerMessage).isEqualTo("Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Invalid UUID string: INVALID")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `404 document not found`() {
    val response = webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, username))
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
      .headers(setDocumentContext(serviceName, username))
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
    val fileBytes = putDocumentInS3(documentUuid, "test_data/warrant-for-remand.pdf")

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
    putDocumentInS3(documentUuid, "test_data/warrant-for-remand.pdf")

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

  private fun WebTestClient.downloadDocument(
    documentUuid: UUID,
    contentType: MediaType,
    contentLength: Long,
    filename: String,
  ) =
    get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(contentType)
      .expectHeader().contentLength(contentLength)
      .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"$filename\""))
      .expectBody(ByteArray::class.java)
      .returnResult()
}
