package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class ReplaceDocumentMetadataIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"B2345CD\" }")
  private val serviceName = "Replaced metadata using service name"
  private val username = "REPLACED_BY_USERNAME"

  @Test
  fun unauthorised() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `forbidden - no roles`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `forbidden - document reader`() {
    webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request - missing service name header`() {
    val response = webTestClient.put()
      .uri("/documents/$documentUuid/metadata")
      .bodyValue(metadata)
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

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `response contains replaced metadata`() {
    val response = webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    assertThat(response.metadata).isEqualTo(metadata)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `document contains replaced metadata`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.metadata).isEqualTo(metadata)
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `metadata history contains original metadata`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    assertThat(entity.documentMetadataHistory().single().metadata)
      .isEqualTo(JacksonUtil.toJsonNode("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"))
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `metadata history uses document context`() {
    webTestClient.replaceDocumentMetadata(documentUuid, metadata)

    val entity = repository.findByDocumentUuid(documentUuid)!!

    with(entity.documentMetadataHistory().single()) {
      assertThat(supersededTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(supersededByServiceName).isEqualTo(serviceName)
      assertThat(supersededByUsername).isEqualTo(username)
    }
  }

  private fun WebTestClient.replaceDocumentMetadata(
    documentUUID: UUID,
    metadata: JsonNode,
  ) =
    put()
      .uri("/documents/$documentUUID/metadata")
      .bodyValue(metadata)
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isAccepted
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DocumentModel::class.java)
      .returnResult().responseBody!!
}