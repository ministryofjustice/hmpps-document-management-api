package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class DeleteDocumentIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  private val documentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val serviceName = "Deleted using service name"
  private val username = "DELETED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}")
      .headers(setAuthorisation())
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document reader`() {
    webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - missing service name header`() {
    val response = webTestClient.delete()
      .uri("/documents/${UUID.randomUUID()}")
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
    val response = webTestClient.delete()
      .uri("/documents/INVALID")
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
  fun `204 no content when document unique identifier not found`() {
    webTestClient.deleteDocument(UUID.randomUUID())
  }

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `delete uses document context`() {
    webTestClient.deleteDocument(documentUuid)

    val entity = repository.findByDocumentUuidIncludingSoftDeleted(documentUuid)!!

    with(entity) {
      assertThat(deletedTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(deletedByServiceName).isEqualTo(serviceName)
      assertThat(deletedByUsername).isEqualTo(username)
    }
  }

  private fun WebTestClient.deleteDocument(documentUuid: UUID) =
    delete()
      .uri("/documents/$documentUuid")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER, ROLE_DOCUMENT_TYPE_SAR)))
      .headers(setDocumentContext(serviceName, username))
      .exchange()
      .expectStatus().isNoContent
}
