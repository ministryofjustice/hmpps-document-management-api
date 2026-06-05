package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult

class DocumentSearchByContentHashIntTest : IntegrationTestBase() {
  private val sharedContentHash = "58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae"

  private val warrantA = "11111111-1111-1111-1111-111111111111"
  private val warrantB = "22222222-2222-2222-2222-222222222222"
  private val sarC = "44444444-4444-4444-4444-444444444444"

  @Sql("classpath:test_data/document-search-content-hash.sql")
  @Test
  fun `matches the exact content hash and excludes other hashes`() {
    val response = searchByContentHash(
      sharedContentHash,
      roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_SAR),
    )

    assertThat(response.results.map { it.documentUuid.toString() })
      .containsExactlyInAnyOrder(warrantA, warrantB, sarC)
  }

  @Sql("classpath:test_data/document-search-content-hash.sql")
  @Test
  fun `excludes document types the caller is not authorised for`() {
    val response = searchByContentHash(sharedContentHash, roles = listOf(ROLE_DOCUMENT_READER))

    assertThat(response.results.map { it.documentUuid.toString() })
      .containsExactlyInAnyOrder(warrantA, warrantB)
  }

  @Sql("classpath:test_data/document-search-content-hash.sql")
  @Test
  fun `matching is case insensitive on the supplied hash`() {
    val response = searchByContentHash(
      sharedContentHash.uppercase(),
      roles = listOf(ROLE_DOCUMENT_READER),
    )

    assertThat(response.results.map { it.documentUuid.toString() })
      .containsExactlyInAnyOrder(warrantA, warrantB)
  }

  private fun searchByContentHash(hash: String, roles: List<String>): DocumentSearchResult = webTestClient.post()
    .uri("/documents/search")
    .bodyValue(DocumentSearchRequest(documentTypes = null, metadata = null, fileContentHash = hash))
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext())
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(DocumentSearchResult::class.java)
    .returnResult().responseBody!!
}
