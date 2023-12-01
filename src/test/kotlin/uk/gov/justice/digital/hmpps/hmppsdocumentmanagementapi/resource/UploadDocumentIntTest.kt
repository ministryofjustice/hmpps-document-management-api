package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentFileService
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class UploadDocumentIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  @Autowired
  lateinit var fileService: DocumentFileService

  private val metadata = JacksonUtil.toJsonNode("{ \"caseReferenceNumber\": \"T20231234\", \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }")
  private val serviceName = "Uploaded via service name"
  private val username = "UPLOADED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document reader`() {
    webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
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
  fun `400 bad request - invalid document type`() {
    val response = webTestClient.post()
      .uri("/documents/INVALID/${UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Parameter documentType must be one of the following HMCTS_WARRANT, SUBJECT_ACCESS_REQUEST_REPORT")
      assertThat(developerMessage).isEqualTo("Failed to convert value of type 'java.lang.String' to required type 'uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType'; Failed to convert from type [java.lang.String] to type [@org.springframework.web.bind.annotation.PathVariable @io.swagger.v3.oas.annotations.Parameter uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType] for value [INVALID]")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - invalid document uuid`() {
    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/INVALID")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
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
  fun `400 bad request - no body`() {
    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Current request is not a multipart request")
      assertThat(developerMessage).isEqualTo("Current request is not a multipart request")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - missing file`() {
    val builder = MultipartBodyBuilder()
    builder.part("metadata", metadata)

    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .bodyValue(builder.build())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Required part 'file' is not present.")
      assertThat(developerMessage).isEqualTo("Required part 'file' is not present.")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - missing metadata`() {
    val builder = MultipartBodyBuilder()
    builder.part("file", ClassPathResource("test_data/warrant-for-remand.pdf"))

    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .bodyValue(builder.build())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Required part 'metadata' is not present.")
      assertThat(developerMessage).isEqualTo("Required part 'metadata' is not present.")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `409 conflict - document unique identifier already used`() {
    val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")

    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/$documentUuid")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(409)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Document with UUID '$documentUuid' already uploaded.")
      assertThat(developerMessage).isEqualTo("Document with UUID '$documentUuid' already uploaded.")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `409 conflict - document unique identifier already used - soft deleted document`() {
    val documentUuid = UUID.fromString("c671814d-7c32-4394-b46d-a70957148925")

    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/$documentUuid")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(409)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Document with UUID '$documentUuid' already uploaded.")
      assertThat(developerMessage).isEqualTo("Document with UUID '$documentUuid' already uploaded.")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `response contains supplied document type and unique id`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val documentUuid = UUID.randomUUID()

    val response = webTestClient.uploadDocument(documentType, documentUuid)

    assertThat(response.documentUuid).isEqualTo(documentUuid)
    assertThat(response.documentType).isEqualTo(documentType)
  }

  @Test
  fun `document contains replaced metadata`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val documentUuid = UUID.randomUUID()

    webTestClient.uploadDocument(documentType, documentUuid)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.documentUuid).isEqualTo(documentUuid)
    assertThat(entity.documentType).isEqualTo(documentType)
  }

  @Test
  fun `response contains supplied file info`() {
    val response = webTestClient.uploadDocument()

    with(response) {
      assertThat(filename).isEqualTo("warrant-for-remand")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(20688)
      assertThat(fileHash).isEqualTo("")
      assertThat(mimeType).isEqualTo("application/pdf")
    }
  }

  @Test
  fun `document contains supplied file info`() {
    val response = webTestClient.uploadDocument()

    val entity = repository.findByDocumentUuid(response.documentUuid)!!

    with(entity) {
      assertThat(filename).isEqualTo("warrant-for-remand")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(20688)
      assertThat(fileHash).isEqualTo("")
      assertThat(mimeType).isEqualTo("application/pdf")
    }
  }

  @Test
  fun `response contains supplied metadata`() {
    val response = webTestClient.uploadDocument()

    assertThat(response.metadata).isEqualTo(metadata)
  }

  @Test
  fun `document contains supplied metadata`() {
    val response = webTestClient.uploadDocument()

    val entity = repository.findByDocumentUuid(response.documentUuid)!!

    assertThat(entity.metadata).isEqualTo(metadata)
  }

  @Test
  fun `response contains document context`() {
    val response = webTestClient.uploadDocument()

    with(response) {
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo(serviceName)
      assertThat(createdByUsername).isEqualTo(username)
    }
  }

  @Test
  fun `document contains document context`() {
    val response = webTestClient.uploadDocument()

    val entity = repository.findByDocumentUuid(response.documentUuid)!!

    with(entity) {
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo(serviceName)
      assertThat(createdByUsername).isEqualTo(username)
    }
  }

  @Test
  fun `document file stored in S3`() {
    val response = webTestClient.uploadDocument()

    val documentFile = fileService.getDocumentFile(response.documentUuid).readAllBytes()

    assertThat(documentFile).hasSize(20688)
  }

  private fun documentMetadataMultipartBody() =
    MultipartBodyBuilder().apply {
      part("file", ClassPathResource("test_data/warrant-for-remand.pdf"))
      part("metadata", metadata)
    }.build()

  private fun WebTestClient.uploadDocument(
    documentType: DocumentType = DocumentType.HMCTS_WARRANT,
    documentUuid: UUID = UUID.randomUUID(),
  ) =
    post()
      .uri("/documents/$documentType/$documentUuid")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isCreated
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentModel::class.java)
      .returnResult().responseBody!!
}
