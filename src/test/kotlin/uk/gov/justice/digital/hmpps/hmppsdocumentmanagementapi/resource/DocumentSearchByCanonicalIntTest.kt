package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult

class DocumentSearchByCanonicalIntTest : IntegrationTestBase() {
  private val canonical = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
  private val duplicate = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

  @Sql("classpath:test_data/document-search-canonical.sql")
  @Test
  fun `canonical true returns only documents with no duplicate_of`() {
    val response = search(canonical = true)

    assertThat(response.results.map { it.documentUuid.toString() }).containsExactly(canonical)
    assertThat(response.results.single().duplicateOf).isNull()
  }

  @Sql("classpath:test_data/document-search-canonical.sql")
  @Test
  fun `canonical false returns only duplicates`() {
    val response = search(canonical = false)

    assertThat(response.results.map { it.documentUuid.toString() }).containsExactly(duplicate)
    assertThat(response.results.single().duplicateOf.toString()).isEqualTo(canonical)
  }

  @Sql("classpath:test_data/document-search-canonical.sql")
  @Test
  fun `no canonical filter returns both`() {
    val response = search(canonical = null)

    assertThat(response.results.map { it.documentUuid.toString() })
      .containsExactlyInAnyOrder(canonical, duplicate)
  }

  private fun search(canonical: Boolean?): DocumentSearchResult = webTestClient.post()
    .uri("/documents/search")
    .bodyValue(DocumentSearchRequest(documentTypes = listOf(DocumentType.HMCTS_WARRANT), metadata = null, canonical = canonical))
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
    .headers(setDocumentContext())
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(DocumentSearchResult::class.java)
    .returnResult().responseBody!!
}
