package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.S3BucketName
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class DistinguishingMarkImageIntTest : IntegrationTestBase() {
  private val documentType = DocumentType.DISTINGUISHING_MARK_IMAGE
  private val documentUuid = UUID.fromString("dcfa4919-4474-461d-a795-336fbd11438c")
  private val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")
  private val serviceName = "distinguishing-marks-api"
  private val activeCaseLoadId = "KIR"
  private val username = "TEST_USER"

  @Test
  fun `upload document - 403 forbidden - document writer only`() {
    webTestClient.post()
      .uri("/documents/$documentType/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `upload document - 403 forbidden - document type DISTINGUISHING_MARK_IMAGE only`() {
    webTestClient.post()
      .uri("/documents/$documentType/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `upload document success`() {
    val documentUuid = UUID.randomUUID()

    val response = webTestClient.post()
      .uri("/documents/$documentType/$documentUuid")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isCreated
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Document::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(this.documentUuid).isEqualTo(documentUuid)
      assertThat(this.documentType).isEqualTo(this@DistinguishingMarkImageIntTest.documentType)
      assertThat(filename).isEqualTo("distinguishing-mark-image")
      assertThat(fileExtension).isEqualTo("png")
      assertThat(fileSize).isEqualTo(10465)
      assertThat(fileHash).isEqualTo("")
      assertThat(mimeType).isEqualTo("image/png")
      assertThat(metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      assertThat(metadata).isEqualTo(this@DistinguishingMarkImageIntTest.metadata)
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo(serviceName)
      assertThat(createdByUsername).isEqualTo(username)
    }
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `get document - 403 forbidden - document reader only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `get document - 403 forbidden - document type DISTINGUISHING_MARK_IMAGE only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `get document success`() {
    val response = webTestClient.get()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isOk
      .expectBody(Document::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(documentUuid).isEqualTo(this@DistinguishingMarkImageIntTest.documentUuid)
      assertThat(documentType).isEqualTo(DocumentType.DISTINGUISHING_MARK_IMAGE)
      assertThat(filename).isEqualTo("distinguishing-mark-image")
      assertThat(fileExtension).isEqualTo("png")
      assertThat(fileSize).isEqualTo(10465)
      assertThat(fileHash).isEqualTo("d58e3582afa99040e27b92b13c8f2280")
      assertThat(mimeType).isEqualTo("image/png")
      assertThat(metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      assertThat(metadata).isEqualTo(this@DistinguishingMarkImageIntTest.metadata)
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo(serviceName)
      assertThat(createdByUsername).isEqualTo(username)
    }
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `get document file - 403 forbidden - document reader only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `get document file - 403 forbidden - document type DISTINGUISHING_MARK_IMAGE only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `download document success`() {
    val fileBytes = putDocumentInS3()

    val response = webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType("image/png")
      .expectHeader().contentLength(10465)
      .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"distinguishing-mark-image.png\""))
      .expectBody(ByteArray::class.java)
      .returnResult()

    assertThat(response.responseBody).isEqualTo(fileBytes)
  }

  @Test
  fun `search documents - 403 forbidden - document reader only`() {
    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `search documents - 403 forbidden - document type DISTINGUISHING_MARK_IMAGE only`() {
    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search documents success`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentSearchResult::class.java)
      .returnResult().responseBody!!

    with(response.request) {
      assertThat(documentType).isEqualTo(this@DistinguishingMarkImageIntTest.documentType)
      assertThat(metadata).isEqualTo(this@DistinguishingMarkImageIntTest.metadata)
    }
    assertThat(response.results).isNotEmpty()
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `replace document metadata - 403 forbidden - document writer only`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `replace document metadata - 403 forbidden - document type DISTINGUISHING_MARK_IMAGE only`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `replace document metadata success`() {
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"B2345CD\" }")

    val response = webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Document::class.java)
      .returnResult().responseBody!!

    assertThat(response.metadata).isEqualTo(metadata)
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `delete document - 403 forbidden - document writer only`() {
    webTestClient.delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `delete document - 403 forbidden - document type DISTINGUISHING_MARK_IMAGE only`() {
    webTestClient.delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/distinguishing-mark-image-test.sql")
  @Test
  fun `delete document success`() {
    putDocumentInS3()

    webTestClient.delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isNoContent
  }

  private fun documentMetadataMultipartBody() =
    MultipartBodyBuilder().apply {
      part("file", ClassPathResource("test_data/distinguishing-mark-image.png"))
      part("metadata", metadata)
    }.build()

  private fun putDocumentInS3() = putDocumentInS3(documentUuid, "test_data/distinguishing-mark-image.png", S3BucketName.DISTINGUISHING_MARK_IMAGES.value)
}
