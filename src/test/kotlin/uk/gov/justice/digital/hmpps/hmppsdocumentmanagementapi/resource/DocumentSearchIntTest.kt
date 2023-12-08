package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class DocumentSearchIntTest : IntegrationTestBase() {
  private val deletedDocumentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val documentType = DocumentType.HMCTS_WARRANT
  private val metadata: JsonNode = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")
  private val serviceName = "Searched using service name"
  private val username = "SEARCHED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document writer`() {
    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
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
  fun `400 bad request - no body`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Couldn't read request body: Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.searchDocuments(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest,jakarta.servlet.http.HttpServletRequest)")
      assertThat(developerMessage).isEqualTo("Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.searchDocuments(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest,jakarta.servlet.http.HttpServletRequest)")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - document type or metadata criteria must be supplied`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(null, null))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Document type or metadata criteria must be supplied.")
      assertThat(developerMessage).isEqualTo("Document type or metadata criteria must be supplied.")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - metadata property values must not be empty`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(null, JacksonUtil.toJsonNode("{ \"prisonNumber\": \"\" }")))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Metadata property values must be non null or empty strings.")
      assertThat(developerMessage).isEqualTo("Metadata property values must be non null or empty strings.")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `response contains search request`() {
    val response = webTestClient.searchDocuments(documentType, metadata)

    with(response.request) {
      assertThat(documentType).isEqualTo(this@DocumentSearchIntTest.documentType)
      assertThat(metadata).isEqualTo(this@DocumentSearchIntTest.metadata)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `find all warrants`() {
    val response = webTestClient.searchDocuments(documentType, null)

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search warrants by prison number`() {
    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
      assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all document types by prison number - client has document type additional role`() {
    val response = webTestClient.searchDocuments(
      null,
      metadata,
      listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_SAR),
    )

    with(response.results) {
      assertThat(map { it.documentType }).isEqualTo(listOf(DocumentType.HMCTS_WARRANT, DocumentType.SUBJECT_ACCESS_REQUEST_REPORT))
      onEach {
        assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      }
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all document types by prison number - client does not have document type additional role`() {
    val response = webTestClient.searchDocuments(
      null,
      metadata,
      listOf(ROLE_DOCUMENT_READER),
    )

    with(response.results) {
      assertThat(map { it.documentType }).isEqualTo(listOf(DocumentType.HMCTS_WARRANT))
      onEach {
        assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      }
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search metadata is case insensitive`() {
    val metadata = JacksonUtil.toJsonNode("{ \"court\": \"stafford crown\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach {
      assertThat(it.metadata["court"].asText()).isEqualTo("Stafford Crown")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search metadata contains text`() {
    val metadata = JacksonUtil.toJsonNode("{ \"court\": \"agist\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach {
      assertThat(it.metadata["court"].asText()).contains("Magistrates")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search string array metadata property`() {
    val metadata = JacksonUtil.toJsonNode("{ \"previousPrisonNumbers\": \"A1234BC\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach { document ->
      assertThat(document.metadata["previousPrisonNumbers"].map { it.asText() }).contains("A1234BC")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search by multiple metadata properties`() {
    val metadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"SFI\", \"prisonNumber\": \"D4567EF\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach {
      assertThat(it.metadata["prisonCode"].asText()).isEqualTo("SFI")
      assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("D4567EF")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search does not return deleted documents`() {
    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach {
      assertThat(it.documentUuid).isNotEqualTo(deletedDocumentUuid)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `audits event`() {
    webTestClient.searchDocuments(documentType, metadata)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()
    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENTS_SEARCHED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), Assertions.within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)
      with(objectMapper.readValue<DocumentsSearchedEvent>(details)) {
        assertThat(request).isEqualTo(DocumentSearchRequest(documentType, metadata))
        assertThat(resultsCount).isEqualTo(1)
      }
    }
  }

  private fun WebTestClient.searchDocuments(
    documentType: DocumentType?,
    metadata: JsonNode?,
    roles: List<String> = listOf(ROLE_DOCUMENT_READER),
  ) =
    post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = roles))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentSearchResult::class.java)
      .returnResult().responseBody!!
}
