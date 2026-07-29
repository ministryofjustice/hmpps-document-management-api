package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.data.domain.Sort.Direction
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FacetValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.FilterOperator
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.MetadataFilter
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ORDER_BY_DIRECTION_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ORDER_BY_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.PAGE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.PAGE_SIZE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.TOTAL_RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY
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

  private val serviceName = "Searched using service name"
  private val activeCaseLoadId = "KPI"
  private val username = "SEARCHED_BY_USERNAME"
  private val duplicatedDocumentUUID = UUID.fromString("91211779-fccc-4e40-a7f5-27decf107df4")

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `find all warrants`() {
    val response = webTestClient.facetSearchDocuments(
      DocumentFacetSearchRequest(
        documentTypes = listOf(documentType),
      ),
    )

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
    }
    val deleted = response.results.find { it.documentUuid == deletedDocumentUuid }
    assertThat(deleted).isNull()
    val duplicated = response.results.find { it.documentUuid == duplicatedDocumentUUID }
    assertThat(duplicated).isNotNull()
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `find non duplicated warrants`() {
    val response = webTestClient.facetSearchDocuments(
      DocumentFacetSearchRequest(
        documentTypes = listOf(documentType),
        canonical = true,
      ),
    )

    response.results.onEach {
      assertThat(it.documentType).isEqualTo(documentType)
    }
    val deleted = response.results.find { it.documentUuid == deletedDocumentUuid }
    assertThat(deleted).isNull()
    val duplicated = response.results.find { it.documentUuid == duplicatedDocumentUUID }
    assertThat(duplicated).isNull()
  }

  @Nested
  @DisplayName("Facet tests")
  inner class FacetTests {

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Facet array type`() {
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          facets = listOf(
            FacetRequest(
              "caseReferences",
              FacetType.ARRAY,
            ),
          ),
        ),
      )

      assertThat(response.facets).isEqualTo(
        mapOf(
          "caseReferences" to FacetResult(
            listOf(
              FacetValue(
                value = "CASE001",
                count = 5,
              ),
              FacetValue(
                value = "CASE002",
                count = 2,
              ),
              FacetValue(
                value = "CASE003",
                count = 1,
              ),
            ),
          ),
        ),
      )
    }

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Facet value type`() {
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          facets = listOf(
            FacetRequest(
              "prisonCode",
              FacetType.VALUE,
            ),
          ),
        ),
      )

      assertThat(response.facets).isEqualTo(
        mapOf(
          "prisonCode" to FacetResult(
            listOf(
              FacetValue(
                value = "SFI",
                count = 3,
              ),
              FacetValue(
                value = "KMI",
                count = 1,
              ),
              FacetValue(
                value = "MDI",
                count = 1,
              ),
              FacetValue(
                value = "RSI",
                count = 1,
              ),
            ),
          ),
        ),
      )
    }
  }

  @Nested
  @DisplayName("Base filter tests")
  inner class BaseFilterTests {

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Base filter out of array`() {
      val filterCaseOne = "CASE001"
      val filterCaseTwo = "CASE002"
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          baseFilters = listOf(
            MetadataFilter(
              field = "caseReferences",
              operator = FilterOperator.IN,
              value = "$filterCaseOne,$filterCaseTwo",
            ),
          ),
        ),
      )
      val anyResultsWithOtherCase = response.results.any {
        val references = it.metadata.get("caseReferences").asArray().map { it.asString() }
        !references.contains(filterCaseOne) && !references.contains(filterCaseTwo)
      }
      assertThat(anyResultsWithOtherCase).isFalse
    }

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Base filter not exists`() {
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          baseFilters = listOf(
            MetadataFilter(
              field = "caseReferences",
              operator = FilterOperator.NOT_EXISTS,
              value = null,
            ),
          ),
        ),
      )
      assertThat(response.results.all { !it.metadata.has("caseReferences") }).isTrue
    }

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Base filter exists`() {
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          baseFilters = listOf(
            MetadataFilter(
              field = "caseReferences",
              operator = FilterOperator.EXISTS,
              value = null,
            ),
          ),
        ),
      )
      assertThat(response.results.all { it.metadata.has("caseReferences") }).isTrue
    }

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Base filter equals`() {
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          baseFilters = listOf(
            MetadataFilter(
              field = "status",
              operator = FilterOperator.EQUALS,
              value = "ACTIVE",
            ),
          ),
        ),
      )
      assertThat(response.results.all { it.metadata.get("status").asString() == "ACTIVE" }).isTrue
    }

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Base filter not equals`() {
      val response = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          baseFilters = listOf(
            MetadataFilter(
              field = "status",
              operator = FilterOperator.NOT_EQUALS,
              value = "ACTIVE",
            ),
          ),
        ),
      )
      assertThat(response.results.all { it.metadata.get("status").asString() != "ACTIVE" }).isTrue
    }
  }

  @Nested
  @DisplayName("Order and pagination tests")
  inner class OrderAndPaginationTests {

    @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
    @Test
    fun `search limits results to page size and returns total results count`() {
      val response = webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType), pageSize = 3))

      with(response) {
        assertThat(results).hasSize(3)
        assertThat(totalResultsCount).isEqualTo(5)
      }
    }

    @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
    @Test
    fun `search skips to second page and returns total results count`() {
      val response = webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType), page = 1, pageSize = 3))

      with(response) {
        assertThat(results).hasSize(2)
        assertThat(totalResultsCount).isEqualTo(5)
      }
    }

    @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
    @Test
    fun `search returns no results for page out of range`() {
      val response = webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType), page = 2, pageSize = 3))

      with(response) {
        assertThat(results).isEmpty()
        assertThat(totalResultsCount).isEqualTo(5)
      }
    }

    @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
    @Test
    fun `default ordering is by created time descending`() {
      val response = webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType)))

      assertThat(response.results).containsExactlyElementsOf(
        response.results.sortedByDescending { it.createdTime },
      )
    }

    @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
    @Test
    fun `order by file size ascending`() {
      val response = webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType), orderBy = DocumentSearchOrderBy.FILESIZE, orderByDirection = Direction.ASC))

      assertThat(response.results).containsExactlyElementsOf(
        response.results.sortedBy { it.fileSize },
      )
    }

    @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
    @Test
    fun `order by uses created time to resolve equal values`() {
      val response = webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType), orderBy = DocumentSearchOrderBy.FILE_EXTENSION, orderByDirection = Direction.ASC))

      assertThat(response.results).containsExactlyElementsOf(
        response.results.sortedWith(compareBy<Document> { it.fileExtension }.thenBy { it.createdTime }),
      )
    }
  }

  @Nested
  @DisplayName("Facet filter tests")
  inner class FacetFilterTests {

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `Facet filters do not change facet count numbers`() {
      val responseWithNoFacetFilter = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          facets = listOf(
            FacetRequest(
              "caseReferences",
              FacetType.ARRAY,
            ),
          ),
        ),
      )
      val filterCaseTwo = "CASE002"
      val responseWithFacetFilter = webTestClient.facetSearchDocuments(
        DocumentFacetSearchRequest(
          documentTypes = listOf(documentType),
          facetFilters = listOf(
            MetadataFilter(
              field = "caseReferences",
              operator = FilterOperator.IN,
              value = "$filterCaseTwo",
            ),

          ),
          facets = listOf(
            FacetRequest(
              "caseReferences",
              FacetType.ARRAY,
            ),
          ),
        ),
      )

      assertThat(responseWithNoFacetFilter.totalResultsCount > responseWithFacetFilter.totalResultsCount)
      assertThat(responseWithNoFacetFilter.facets).isEqualTo(responseWithFacetFilter.facets)
    }
  }

  @Nested
  @DisplayName("Failure tests")
  inner class FailureTests {
    @Test
    fun `401 unauthorised`() {
      webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(DocumentFacetSearchRequest(listOf(documentType)))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `403 forbidden - no roles`() {
      webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(DocumentFacetSearchRequest(listOf(documentType)))
        .headers(setAuthorisation())
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `403 forbidden - document writer`() {
      webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(DocumentFacetSearchRequest(listOf(documentType)))
        .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `400 bad request - missing service name header`() {
      val response = webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(DocumentFacetSearchRequest(listOf(documentType)))
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
        .uri("/documents/facet/search")
        .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult().responseBody

      with(response!!) {
        assertThat(status).isEqualTo(400)
        assertThat(errorCode).isNull()
        assertThat(userMessage).isEqualTo("Validation failure: Couldn't read request body: Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchResult uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.facetSearchDocuments(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchRequest,jakarta.servlet.http.HttpServletRequest)")
        assertThat(developerMessage).isEqualTo("Required request body is missing: public uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchResult uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentController.facetSearchDocuments(uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFacetSearchRequest,jakarta.servlet.http.HttpServletRequest)")
        assertThat(moreInfo).isNull()
      }
    }

    @Test
    fun `400 bad request - page must be 0 or greater`() {
      val response = webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(DocumentFacetSearchRequest(listOf(documentType), page = -1))
        .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult().responseBody

      with(response!!) {
        assertThat(status).isEqualTo(400)
        assertThat(errorCode).isNull()
        assertThat(userMessage).isEqualTo("Validation failure: Page must be 0 or greater.")
        assertThat(developerMessage).isEqualTo("Page must be 0 or greater.")
        assertThat(moreInfo).isNull()
      }
    }

    @Test
    fun `400 bad request - page size must be between 1 and 100`() {
      val response = webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(DocumentFacetSearchRequest(listOf(documentType), pageSize = 0))
        .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult().responseBody

      with(response!!) {
        assertThat(status).isEqualTo(400)
        assertThat(errorCode).isNull()
        assertThat(userMessage).isEqualTo("Validation failure: Page size must be between 1 and 200.")
        assertThat(developerMessage).isEqualTo("Page size must be between 1 and 200.")
        assertThat(moreInfo).isNull()
      }
    }

    @Test
    fun `400 bad request - invalid order by`() {
      webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(jsonMapper.readTree("{ \"documentTypes\": [\"${documentType.name}\"], \"orderBy\": \"INVALID\" }"))
        .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult().responseBody
    }

    @Test
    fun `400 bad request - invalid order by direction`() {
      webTestClient.post()
        .uri("/documents/facet/search")
        .bodyValue(jsonMapper.readTree("{ \"documentTypes\": [\"${documentType.name}\"], \"orderByDirection\": \"INVALID\" }"))
        .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
        .headers(setDocumentContext())
        .exchange()
        .expectStatus().isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult().responseBody
    }
  }

  @Nested
  @DisplayName("Audit tests")
  inner class AuditTests {

    @Sql("classpath:test_data/document-search.sql")
    @Test
    fun `tracks event`() {
      webTestClient.facetSearchDocuments(DocumentFacetSearchRequest(listOf(documentType)))

      val customEventProperties = argumentCaptor<Map<String, String>>()
      val customEventMetrics = argumentCaptor<Map<String, Double>>()
      verify(telemetryClient).trackEvent(
        eq(EventType.DOCUMENTS_FACET_SEARCHED.name),
        customEventProperties.capture(),
        customEventMetrics.capture(),
      )

      with(customEventProperties.firstValue) {
        assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(serviceName)
        assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(activeCaseLoadId)
        assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(username)
        assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(documentType.name)
        assertThat(this[DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY]).isEqualTo(documentType.description)
        assertThat(this[ORDER_BY_PROPERTY_KEY]).isEqualTo(DocumentSearchOrderBy.CREATED_TIME.name)
        assertThat(this[ORDER_BY_DIRECTION_PROPERTY_KEY]).isEqualTo(Direction.DESC.name)
      }

      with(customEventMetrics.firstValue) {
        assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
        assertThat(this[PAGE_PROPERTY_KEY]).isEqualTo(0.0)
        assertThat(this[PAGE_SIZE_PROPERTY_KEY]).isEqualTo(10.0)
        assertThat(this[RESULTS_COUNT_METRIC_KEY]).isEqualTo(6.0)
        assertThat(this[TOTAL_RESULTS_COUNT_METRIC_KEY]).isEqualTo(6.0)
      }
    }
  }
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
