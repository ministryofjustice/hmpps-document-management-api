package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort.Direction
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetType
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature.hmpps.audit.enabled=true",
  ],
)
class DocumentFacetSearchIntTest : IntegrationTestBase() {
  private val jsonMapper = ObjectMapper()

  private val deletedDocumentUuid = UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37")
  private val documentType = DocumentType.HMCTS_WARRANT

  private val metadata: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"A1234BC\" }")

  private val serviceName = "Searched using service name"
  private val activeCaseLoadId = "KPI"
  private val username = "SEARCHED_BY_USERNAME"

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `find all warrants`() {
    val response = webTestClient.facetSearchDocuments(
      DocumentFacetSearchRequest(
        documentTypes = listOf(documentType),
        page = 0,
        pageSize = 10,
        orderBy = DocumentSearchOrderBy.CREATED_TIME,
        orderByDirection = Direction.DESC,
        baseFilters = emptyList(),
        facetFilters = emptyList(),
        facets = listOf(
          FacetRequest(
            "caseReferences",
            FacetType.ARRAY,
          ),
          FacetRequest(
            "prisonCode",
            FacetType.VALUE,
          ),
        ),
      ),
    )

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
    }
  }

  private fun WebTestClient.searchDocuments(
    documentTypes: List<DocumentType>?,
    metadata: JsonNode?,
    page: Int = 0,
    pageSize: Int = 10,
    orderBy: DocumentSearchOrderBy = DocumentSearchOrderBy.CREATED_TIME,
    orderByDirection: Direction = Direction.DESC,
    roles: List<String> = listOf(ROLE_DOCUMENT_READER),
  ) = post()
    .uri("/documents/search")
    .bodyValue(DocumentSearchRequest(documentTypes, metadata, page, pageSize, orderBy, orderByDirection))
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(DocumentSearchResult::class.java)
    .returnResult().responseBody!!

  private fun WebTestClient.facetSearchDocuments(
    search: DocumentFacetSearchRequest,
    roles: List<String> = listOf(ROLE_DOCUMENT_READER),
  ) = post()
    .uri("/documents/facet/search")
    .bodyValue(search)
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(DocumentFacetSearchResult::class.java)
    .returnResult().responseBody!!
}
