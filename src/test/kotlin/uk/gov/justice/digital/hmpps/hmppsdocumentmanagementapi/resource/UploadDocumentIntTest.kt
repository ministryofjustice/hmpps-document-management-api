package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.apache.commons.lang3.StringUtils
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
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.TestConstants
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentFileService
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
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@TestPropertySource(
  properties = [
    "feature.hmpps.audit.enabled=true",
  ],
)
class UploadDocumentIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  @Autowired
  lateinit var fileService: DocumentFileService

  private val metadata = ObjectMapper().readTree("{ \"caseReferenceNumber\": \"T20231234\", \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }")
  private val serviceName = "Uploaded via service name"
  private val activeCaseLoadId = "RSI"
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
      .uri("/documents/${TestConstants.INVALID_DOCUMENT_TYPE}/${UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Parameter documentType must be one of the following ${StringUtils.join(DocumentType.entries.toTypedArray(), ", ")}")
      assertThat(developerMessage).isEqualTo(String.format(TestConstants.INVALID_DOC_TYPE_EXCEPTION_MESSAGE_TEMPLATE, TestConstants.INVALID_DOCUMENT_TYPE))
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - invalid document uuid`() {
    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${TestConstants.INVALID_UUID}")
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
    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
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
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
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
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
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

  @Test
  fun `400 bad request - uploading file with virus`() {
    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/${UUID.randomUUID()}")
      .bodyValue(documentMetadataMultipartBody("eicar.txt"))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Document file virus scan FAILED with result stream: Eicar-Test-Signature FOUND and signature Eicar-Test-Signature")
      assertThat(developerMessage).isEqualTo("Document file virus scan FAILED with result stream: Eicar-Test-Signature FOUND and signature Eicar-Test-Signature")
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
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
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
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
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
  fun `document contains supplied document type and unique id`() {
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
      assertThat(documentFilename).isEqualTo("warrant-for-remand.pdf")
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

    val documentFile = fileService.getDocumentFile(response.documentUuid, DocumentType.PIC_CASE_DOCUMENTS).readAllBytes()

    assertThat(documentFile).hasSize(20688)
  }

  @Test
  fun `audits event`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val documentUuid = UUID.randomUUID()

    webTestClient.uploadDocument(documentType, documentUuid)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENT_UPLOADED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      with(objectMapper.readValue<DocumentModel>(details)) {
        assertThat(this.documentUuid).isEqualTo(documentUuid)
        assertThat(this.documentType).isEqualTo(documentType)
        assertThat(documentFilename).isEqualTo("warrant-for-remand.pdf")
        assertThat(filename).isEqualTo("warrant-for-remand")
        assertThat(fileExtension).isEqualTo("pdf")
        assertThat(fileSize).isEqualTo(20688)
        assertThat(fileHash).isEqualTo("")
        assertThat(mimeType).isEqualTo("application/pdf")
        assertThat(metadata).isEqualTo(metadata)
        assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
        assertThat(createdByServiceName).isEqualTo(serviceName)
        assertThat(createdByUsername).isEqualTo(username)
      }
    }
  }

  @Test
  fun `tracks event`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val documentUuid = UUID.randomUUID()

    webTestClient.uploadDocument(documentType, documentUuid)

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENT_UPLOADED.name), customEventProperties.capture(), customEventMetrics.capture())

    with(customEventProperties.firstValue) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(documentUuid.toString())
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(documentType.name)
      assertThat(this[DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY]).isEqualTo(DocumentType.HMCTS_WARRANT.description)
      assertThat(this[FILE_EXTENSION_PROPERTY_KEY]).isEqualTo("pdf")
      assertThat(this[MIME_TYPE_PROPERTY_KEY]).isEqualTo("application/pdf")
    }

    with(customEventMetrics.firstValue) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(20688.0)
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(3.0)
    }
  }

  private fun documentMetadataMultipartBody(file: String = "warrant-for-remand.pdf") = MultipartBodyBuilder().apply {
    part("file", ClassPathResource("test_data/$file"))
    part("metadata", metadata)
  }.build()

  private fun WebTestClient.uploadDocument(
    documentType: DocumentType = DocumentType.HMCTS_WARRANT,
    documentUuid: UUID = UUID.randomUUID(),
  ) = post()
    .uri("/documents/$documentType/$documentUuid")
    .bodyValue(documentMetadataMultipartBody())
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isCreated
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(DocumentModel::class.java)
    .returnResult().responseBody!!
}
