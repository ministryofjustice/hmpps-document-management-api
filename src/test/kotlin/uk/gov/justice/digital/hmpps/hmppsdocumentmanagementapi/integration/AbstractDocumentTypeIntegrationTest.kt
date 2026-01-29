package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ROLE_DOCUMENT_READER
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ROLE_DOCUMENT_WRITER
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

abstract class AbstractDocumentTypeIntegrationTest : IntegrationTestBase() {
  abstract val documentType: DocumentType
  abstract val documentTypeRole: String
  abstract val documentUuid: UUID
  abstract val metadata: JsonNode
  abstract val serviceName: String
  abstract val activeCaseLoadId: String
  abstract val username: String
  abstract val testFileName: String
  abstract val resourcePathOfTestDocument: String
  abstract val documentFileSize: Long
  abstract val contentType: String
  abstract val bucketName: String
  abstract val testFileHash: String

  @Test
  fun `upload document - 403 forbidden - document writer only`() {
    uploadDocument(
      UUID.randomUUID(),
      resourcePathOfTestDocument,
      listOf(ROLE_DOCUMENT_WRITER),
    ).expectStatus().isForbidden
  }

  @Test
  fun `upload document - 403 forbidden - document type role only`() {
    uploadDocument(
      UUID.randomUUID(),
      resourcePathOfTestDocument,
      listOf(documentTypeRole),
    ).expectStatus().isForbidden
  }

  @Test
  fun `upload document success`() {
    val newUuid = UUID.randomUUID()
    val response = uploadDocument(
      newUuid,
      resourcePathOfTestDocument,
      listOf(ROLE_DOCUMENT_WRITER, documentTypeRole),
    ).expectStatus().isCreated.expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Document::class.java).returnResult().responseBody!!

    assertDocumentDataIsCorrect(newUuid, "", response)
  }

  @Test
  fun `get document - 403 forbidden - document reader only`() {
    getDocument(documentUuid, listOf(ROLE_DOCUMENT_READER)).expectStatus().isForbidden
  }

  @Test
  fun `get document - 403 forbidden - document type role only`() {
    getDocument(documentUuid, listOf(documentTypeRole)).expectStatus().isForbidden
  }

  @Test
  fun `get document success`() {
    val response = getDocument(
      documentUuid,
      listOf(ROLE_DOCUMENT_READER, documentTypeRole),
    ).expectStatus().isOk.expectBody(Document::class.java).returnResult().responseBody!!

    assertDocumentDataIsCorrect(documentUuid, testFileHash, response)
  }

  @Test
  fun `get document file - 403 forbidden - document reader only`() {
    getDocumentFile(documentUuid, listOf(ROLE_DOCUMENT_READER)).expectStatus().isForbidden
  }

  @Test
  fun `get document file - 403 forbidden - document type role only`() {
    getDocumentFile(documentUuid, listOf(documentTypeRole)).expectStatus().isForbidden
  }

  @Test
  fun `download document success`() {
    val fileBytes = putDocumentInS3()

    val response = getDocumentFile(
      documentUuid,
      listOf(ROLE_DOCUMENT_READER, documentTypeRole),
    ).expectStatus().isOk.expectHeader().contentType(contentType).expectHeader()
      .contentLength(documentFileSize).expectHeader()
      .contentDisposition(ContentDisposition.parse("attachment; filename=\"$testFileName\""))
      .expectBody(ByteArray::class.java).returnResult()

    assertThat(response.responseBody).isEqualTo(fileBytes)
  }

  @Test
  fun `search documents - 403 forbidden - document reader only`() {
    searchDocuments(
      DocumentSearchRequest(documentType, metadata),
      listOf(ROLE_DOCUMENT_READER),
    ).expectStatus().isForbidden
  }

  @Test
  fun `search documents - 403 forbidden - document type role only`() {
    searchDocuments(
      DocumentSearchRequest(documentType, metadata),
      listOf(documentTypeRole),
    ).expectStatus().isForbidden
  }

  @Test
  fun `search documents success`() {
    val response = searchDocuments(
      DocumentSearchRequest(documentType, metadata),
      listOf(ROLE_DOCUMENT_READER, documentTypeRole),
    ).expectStatus().isOk.expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentSearchResult::class.java).returnResult().responseBody!!

    with(response.request) {
      assertThat(documentType).isEqualTo(this.documentType)
      assertThat(metadata).isEqualTo(this.metadata)
    }
    assertThat(response.results).isNotEmpty()
  }

  @Test
  fun `replace document metadata - 403 forbidden - document writer only`() {
    replaceMetadata(documentUuid, metadata, listOf(ROLE_DOCUMENT_WRITER)).expectStatus().isForbidden
  }

  @Test
  fun `replace document metadata - 403 forbidden - document type role only`() {
    replaceMetadata(documentUuid, metadata, listOf(documentTypeRole)).expectStatus().isForbidden
  }

  @Test
  fun `replace document metadata success`() {
    // FIX: Use the new ObjectMapper directly instead of JacksonUtil
    val newMetadata = ObjectMapper().readTree("{ \"sarCaseReference\": \"SAR-2345\", \"prisonNumber\": \"B2345CD\" }")

    val response = replaceMetadata(
      documentUuid,
      newMetadata,
      listOf(ROLE_DOCUMENT_WRITER, documentTypeRole),
    ).expectStatus().isOk.expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Document::class.java).returnResult().responseBody!!

    assertThat(response.metadata).isEqualTo(newMetadata)
  }

  @Test
  fun `delete document - 403 forbidden - document writer only`() {
    deleteDocument(documentUuid, listOf(ROLE_DOCUMENT_WRITER)).expectStatus().isForbidden
  }

  @Test
  fun `delete document - 403 forbidden - document type role only`() {
    deleteDocument(documentUuid, listOf(documentTypeRole)).expectStatus().isForbidden
  }

  @Test
  fun `delete document success`() {
    putDocumentInS3()

    deleteDocument(
      documentUuid,
      listOf(ROLE_DOCUMENT_WRITER, documentTypeRole),
    ).expectStatus().isNoContent
  }

  private fun putDocumentInS3() = putDocumentInS3(
    documentUuid,
    resourcePathOfTestDocument,
    bucketName,
  )

  private fun uploadDocument(
    documentUuid: UUID,
    pathOfResourceToUpload: String,
    roles: List<String>,
  ): WebTestClient.ResponseSpec = webTestClient.post().uri("/documents/$documentType/$documentUuid")
    .bodyValue(documentMetadataMultipartBody(pathOfResourceToUpload))
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username)).exchange()

  private fun getDocument(documentUuid: UUID, roles: List<String>): WebTestClient.ResponseSpec = webTestClient.get().uri("/documents/$documentUuid")
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username)).exchange()

  internal fun getDocumentFile(
    documentUuid: UUID,
    roles: List<String>,
  ): WebTestClient.ResponseSpec = webTestClient.get().uri("/documents/$documentUuid/file")
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username)).exchange()

  private fun searchDocuments(
    searchRequest: DocumentSearchRequest,
    roles: List<String>,
  ): WebTestClient.ResponseSpec = webTestClient.post().uri("/documents/search").bodyValue(searchRequest)
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username)).exchange()

  private fun replaceMetadata(
    documentUuid: UUID,
    newMetadata: JsonNode,
    roles: List<String>,
  ): WebTestClient.ResponseSpec = webTestClient.put().uri("/documents/$documentUuid/metadata").bodyValue(newMetadata)
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username)).exchange()

  private fun deleteDocument(
    documentUuid: UUID,
    roles: List<String>,
  ): WebTestClient.ResponseSpec = webTestClient.delete().uri("/documents/$documentUuid")
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username)).exchange()

  private fun documentMetadataMultipartBody(pathOfResourceToUpload: String) = MultipartBodyBuilder().apply {
    part("file", ClassPathResource(pathOfResourceToUpload))
    part("metadata", metadata)
  }.build()

  private fun assertDocumentDataIsCorrect(expectedUuid: UUID, expectedFileHash: String, response: Document) {
    with(response) {
      val filenameParts = this@AbstractDocumentTypeIntegrationTest.testFileName.split(".")
      assertThat(this.documentUuid).isEqualTo(expectedUuid)
      assertThat(this.documentType).isEqualTo(this@AbstractDocumentTypeIntegrationTest.documentType)
      assertThat(this.filename).isEqualTo(filenameParts.first())
      assertThat(this.fileExtension).isEqualTo(filenameParts.last())
      assertThat(this.fileSize).isEqualTo(this@AbstractDocumentTypeIntegrationTest.documentFileSize)
      assertThat(this.fileHash).isEqualTo(expectedFileHash)
      assertThat(this.mimeType).isEqualTo(this@AbstractDocumentTypeIntegrationTest.contentType)
      assertThat(this.metadata).isEqualTo(this@AbstractDocumentTypeIntegrationTest.metadata)
      assertThat(this.createdTime).isCloseTo(
        LocalDateTime.now(),
        within(3, ChronoUnit.SECONDS),
      )
      assertThat(this.createdByServiceName).isEqualTo(this@AbstractDocumentTypeIntegrationTest.serviceName)
      assertThat(this.createdByUsername).isEqualTo(this@AbstractDocumentTypeIntegrationTest.username)
    }
  }
}
