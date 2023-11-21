package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import java.util.UUID

class DocumentSearchIntTest : IntegrationTestBase() {
  private val deletedDocumentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")

  @Test
  fun unauthorised() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun forbidden() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(documentType, metadata))
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `response contains search request`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

    with(response.request) {
      assertThat(this.documentType).isEqualTo(documentType)
      assertThat(this.metadata).isEqualTo(metadata)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search warrants by prison number`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
      assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all document types by prison number`() {
    val documentType = null
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

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
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"court\": \"stafford crown\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

    response.results.onEach {
      assertThat(it.metadata["court"].asText()).isEqualTo("Stafford Crown")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search metadata contains text`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"court\": \"agist\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

    response.results.onEach {
      assertThat(it.metadata["court"].asText()).contains("Magistrates")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search string array metadata property`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"previousPrisonNumbers\": \"A1234BC\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

    response.results.onEach { document ->
      assertThat(document.metadata["previousPrisonNumbers"].map { it.asText() }).contains("A1234BC")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search by multiple metadata properties`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"SFI\", \"prisonNumber\": \"D4567EF\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

    response.results.onEach {
      assertThat(it.metadata["prisonCode"].asText()).isEqualTo("SFI")
      assertThat(it.metadata["prisonNumber"].asText()).isEqualTo("D4567EF")
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search does not return deleted documents`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    val response = webTestClient.searchDocuments(documentType, metadata)!!

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
      .expectStatus().isAccepted
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentSearchResult::class.java)
      .returnResult().responseBody
}
