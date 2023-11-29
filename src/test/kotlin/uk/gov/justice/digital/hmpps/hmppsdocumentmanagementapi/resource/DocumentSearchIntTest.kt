package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import java.util.UUID

class DocumentSearchIntTest : IntegrationTestBase() {
  private val deletedDocumentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  val documentType = DocumentType.HMCTS_WARRANT
  val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

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
      assertThat(userMessage).isEqualTo("Validation failure: Couldn't read request body: Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.searchDocuments(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest)")
      assertThat(developerMessage).isEqualTo("Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.searchDocuments(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest)")
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
  fun `search warrants by prison number`() {
    val response = webTestClient.searchDocuments(documentType, metadata)

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
      assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all document types by prison number`() {
    val response = webTestClient.searchDocuments(null, metadata)

    with(response.results) {
      assertThat(map { it.documentType }).isEqualTo(listOf(DocumentType.HMCTS_WARRANT, DocumentType.SUBJECT_ACCESS_REQUEST_REPORT))
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

  private fun WebTestClient.searchDocuments(
    documentType: DocumentType?,
    metadata: JsonNode,
  ) =
    post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentSearchResult::class.java)
      .returnResult().responseBody!!
}
