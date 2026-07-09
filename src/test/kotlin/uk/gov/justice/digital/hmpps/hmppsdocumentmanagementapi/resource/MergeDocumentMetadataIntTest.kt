package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.TestConstants
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.assertIsDocumentWithNoMetadataHistoryId1
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataMergedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
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
class MergeDocumentMetadataIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"B2345CD\" }")
  private val expectedMergedMetadata = ObjectMapper().readTree("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"B2345CD\" }")
  private val serviceName = "Merged metadata using service name"
  private val activeCaseLoadId = "MDI"
  private val username = "MERGED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.patch()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.patch()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document reader`() {
    webTestClient.patch()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.patch()
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
    val response = webTestClient.patch()
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
    val response = webTestClient.patch()
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
      assertThat(userMessage).isEqualTo("Validation failure: Couldn't read request body: Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.mergeDocumentMetadata(java.util.UUID,tools.jackson.databind.JsonNode,jakarta.servlet.http.HttpServletRequest)")
      assertThat(developerMessage).isEqualTo("Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.mergeDocumentMetadata(java.util.UUID,tools.jackson.databind.JsonNode,jakarta.servlet.http.HttpServletRequest)")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `404 not found`() {
    val response = webTestClient.patch()
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
  fun `response contains merged metadata`() {
    val response = webTestClient.mergeDocumentMetadata(documentUuid, metadata)

    assertThat(response.metadata).isEqualTo(expectedMergedMetadata)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `document contains merged metadata`() {
    webTestClient.mergeDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.metadata).isEqualTo(expectedMergedMetadata)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `metadata history contains original metadata`() {
    webTestClient.mergeDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.documentMetadataHistory().single().metadata)
      .isEqualTo(ObjectMapper().readTree("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"))
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `metadata history uses document context`() {
    webTestClient.mergeDocumentMetadata(documentUuid, metadata)

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
    webTestClient.mergeDocumentMetadata(documentUuid, metadata)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENT_METADATA_MERGED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      with(objectMapper.readValue<DocumentMetadataMergedEvent>(details)) {
        document.assertIsDocumentWithNoMetadataHistoryId1(expectedMergedMetadata)
        assertThat(originalMetadata)
          .isEqualTo(ObjectMapper().readTree("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"))
      }
    }
  }

  private fun WebTestClient.mergeDocumentMetadata(
    documentUuid: UUID,
    metadata: JsonNode,
  ) = patch()
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
