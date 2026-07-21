package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import java.util.UUID

class DocumentFindByUuidsDuplicateCaptureIntTest : IntegrationTestBase() {

  @Sql("classpath:test_data/document-search-canonical.sql")
  @Test
  fun `log the raw find-by-uuids response for a canonical and its duplicate`() {
    val rawBody = webTestClient.post().uri("/documents")
      .headers(setDocumentContext(serviceName = SERVICE_NAME, activeCaseLoadId = CASE_LOAD_ID, username = USERNAME))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .bodyValue(listOf(CANONICAL_UUID, DUPLICATE_UUID))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(String::class.java)
      .returnResult()
      .responseBody

    log.info(
      "\n===== BATCH /documents RAW RESPONSE (canonical {} + duplicate {}) =====\n{}\n===== END RESPONSE =====",
      CANONICAL_UUID,
      DUPLICATE_UUID,
      rawBody,
    )
  }

  private companion object {
    private val log = LoggerFactory.getLogger(DocumentFindByUuidsDuplicateCaptureIntTest::class.java)

    // From document-search-canonical.sql
    val CANONICAL_UUID: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    val DUPLICATE_UUID: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    const val SERVICE_NAME = "court-data-ingestion-api"
    const val CASE_LOAD_ID = "PNI"
    const val USERNAME = "hmcts-getcourtdata"
  }
}
