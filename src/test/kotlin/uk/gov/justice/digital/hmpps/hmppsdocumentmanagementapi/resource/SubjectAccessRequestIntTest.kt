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
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class SubjectAccessRequestIntTest : IntegrationTestBase() {
  private val documentType = DocumentType.SUBJECT_ACCESS_REQUEST_REPORT
  private val documentUuid = UUID.fromString("1f4e2c96-de62-4585-a79a-9a37c5506b1c")
  private val metadata = JacksonUtil.toJsonNode("{ \"sarCaseReference\": \"SAR-1234\", \"prisonNumber\": \"A1234BC\" }")
  private val serviceName = "Manage Subject Access Requests"
  private val username = "SAR_USER"

  @Test
  fun `upload document - 403 forbidden - document writer only`() {
    webTestClient.post()
      .uri("/documents/$documentType/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `upload document - 403 forbidden - document type SAR only`() {
    webTestClient.post()
      .uri("/documents/$documentType/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `upload document success`() {
    val documentUuid = UUID.randomUUID()

    val response = webTestClient.post()
      .uri("/documents/$documentType/$documentUuid")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isCreated
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Document::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(this.documentUuid).isEqualTo(documentUuid)
      assertThat(this.documentType).isEqualTo(this@SubjectAccessRequestIntTest.documentType)
      assertThat(filename).isEqualTo("subject-access-request-report")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(21384)
      assertThat(fileHash).isEqualTo("")
      assertThat(mimeType).isEqualTo("application/pdf")
      assertThat(metadata["sarCaseReference"].asText()).isEqualTo("SAR-1234")
      assertThat(metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      assertThat(metadata).isEqualTo(this@SubjectAccessRequestIntTest.metadata)
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo(serviceName)
      assertThat(createdByUsername).isEqualTo(username)
    }
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `get document - 403 forbidden - document reader only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `get document - 403 forbidden - document type SAR only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `get document success`() {
    val response = webTestClient.get()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isOk
      .expectBody(Document::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(documentUuid).isEqualTo(this@SubjectAccessRequestIntTest.documentUuid)
      assertThat(documentType).isEqualTo(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT)
      assertThat(filename).isEqualTo("subject-access-request-report")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(21384)
      assertThat(fileHash).isEqualTo("d58e3582afa99040e27b92b13c8f2280")
      assertThat(mimeType).isEqualTo("application/pdf")
      assertThat(metadata["sarCaseReference"].asText()).isEqualTo("SAR-1234")
      assertThat(metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      assertThat(metadata).isEqualTo(this@SubjectAccessRequestIntTest.metadata)
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo(serviceName)
      assertThat(createdByUsername).isEqualTo(username)
    }
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `get document file - 403 forbidden - document reader only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `get document file - 403 forbidden - document type SAR only`() {
    webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `download document success`() {
    val fileBytes = putDocumentInS3()

    val response = webTestClient.get()
      .uri("/documents/$documentUuid/file")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_PDF)
      .expectHeader().contentLength(21384)
      .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"subject-access-request-report.pdf\""))
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
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `search documents - 403 forbidden - document type SAR only`() {
    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search documents success`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentSearchResult::class.java)
      .returnResult().responseBody!!

    with(response.request) {
      assertThat(documentType).isEqualTo(this@SubjectAccessRequestIntTest.documentType)
      assertThat(metadata).isEqualTo(this@SubjectAccessRequestIntTest.metadata)
    }
    assertThat(response.results).isNotEmpty()
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `replace document metadata - 403 forbidden - document writer only`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `replace document metadata - 403 forbidden - document type SAR only`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `replace document metadata success`() {
    val metadata = JacksonUtil.toJsonNode("{ \"sarCaseReference\": \"SAR-2345\", \"prisonNumber\": \"B2345CD\" }")

    val response = webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Document::class.java)
      .returnResult().responseBody!!

    assertThat(response.metadata).isEqualTo(metadata)
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `delete document - 403 forbidden - document writer only`() {
    webTestClient.delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `delete document - 403 forbidden - document type SAR only`() {
    webTestClient.delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/subject-access-request-report-id-4.sql")
  @Test
  fun `delete document success`() {
    putDocumentInS3()

    webTestClient.delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isNoContent
  }

  private fun documentMetadataMultipartBody() =
    MultipartBodyBuilder().apply {
      part("file", ClassPathResource("test_data/subject-access-request-report.pdf"))
      part("metadata", metadata)
    }.build()

  private fun putDocumentInS3() = putDocumentInS3(documentUuid, "test_data/subject-access-request-report.pdf")
}
