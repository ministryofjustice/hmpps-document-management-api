package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsS3Properties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import java.util.UUID

class DownloadDocumentIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var hmppsS3Properties: HmppsS3Properties

  @Autowired
  lateinit var s3Client: S3Client

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

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `404 not found`() {
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
    val fileBytes = putDocumentInS3(documentUuid)

    val response = webTestClient.downloadDocument(
      documentUuid,
      MediaType.APPLICATION_PDF,
      20688,
      "warrant_for_remand.pdf",
    )

    assertThat(response.responseBody).isEqualTo(fileBytes)
  }

  private fun putDocumentInS3(documentUuid: UUID): ByteArray {
    val bucketName = hmppsS3Properties.buckets["document-management"]!!.bucketName
    val request = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(documentUuid.toString())
      .build()
    val fileBytes = ClassPathResource("test_data/warrant-for-remand.pdf").contentAsByteArray
    s3Client.putObject(request, RequestBody.fromBytes(fileBytes))
    return fileBytes
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