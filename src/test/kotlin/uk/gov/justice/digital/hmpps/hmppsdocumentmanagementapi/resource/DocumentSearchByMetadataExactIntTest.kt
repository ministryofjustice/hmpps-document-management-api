package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.data.domain.Sort.Direction
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.AuditService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.whenLocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_TYPE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.METADATA_FIELD_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ORDER_BY_DIRECTION_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ORDER_BY_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.PAGE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.PAGE_SIZE_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.TOTAL_RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@TestPropertySource(
  properties = [
    "feature.hmpps.audit.enabled=true",
  ],
)
class DocumentSearchByMetadataExactIntTest : IntegrationTestBase() {
  private val jsonMapper = ObjectMapper()

  private val documentType = DocumentType.HMCTS_WARRANT

  private val metadataJson: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"${PRISON_NUMBER_REQUEST_REPORT}\" }")

  private val serviceName = "Searched using service name"
  private val activeCaseLoadId = "KPI"
  private val username = "SEARCHED_BY_USERNAME"

  @Test
  fun `400 bad request - document type or metadata criteria or metadataExact criteria must be supplied`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(null, null, metadataExact = null))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Document type or metadata criteria must be supplied.")
      assertThat(developerMessage).isEqualTo("Document type or metadata criteria must be supplied.")
      assertThat(moreInfo).isNull()
    }
  }

  @ParameterizedTest
  @CsvSource(
    "{ \"prisonNumber\": \"\" }",
    "{ \"prisonNumbers\": [] }",
    "{ \"prisonNumbers\": [ \"\" ] }",
    "'{ \"prisonNumber\": \"\", \"prisonNumbers\": [ \"V1\" ] }'",
    "'{ \"prisonNumbers\": [ \"V1\" ], \"prisonNumber\": \"\" }'",
    "'{ \"prisonNumber\": \"V1\", \"prisonNumbers\": [ \"\" ] }'",
    "'{ \"prisonNumbers\": [ \"\" ], \"prisonNumber\": \"V1\" }'",
  )
  fun `400 bad request - metadata-exact property values must not be empty`(metadataExact: String) {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(null, null, metadataExact = jsonMapper.readTree(metadataExact)))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Metadata property values must be non null or empty strings.")
      assertThat(developerMessage).isEqualTo("Metadata property values must be non null or empty strings.")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - page must be 0 or greater`() {
    val response = webTestClient.post()
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(null, null, page = -1, metadataExact = metadataJson))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody<ErrorResponse>()
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
      .uri("/documents/search")
      .bodyValue(DocumentSearchRequest(null, null, pageSize = 0, metadataExact = metadataJson))
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Page size must be between 1 and 100.")
      assertThat(developerMessage).isEqualTo("Page size must be between 1 and 100.")
      assertThat(moreInfo).isNull()
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `response contains search request`() {
    val response = webTestClient.searchDocuments(
      listOf(documentType),
      null,
      1,
      2,
      DocumentSearchOrderBy.FILESIZE,
      Direction.ASC,
      metadataExact = metadataJson,
    )

    with(response.request) {
      assertThat(documentType).isEqualTo(this@DocumentSearchByMetadataExactIntTest.documentType)
      assertThat(documentTypes?.contains(this@DocumentSearchByMetadataExactIntTest.documentType) ?: false).isTrue()
      assertThat(metadata).isNullOrEmpty()
      assertThat(page).isEqualTo(1)
      assertThat(pageSize).isEqualTo(2)
      assertThat(orderBy).isEqualTo(DocumentSearchOrderBy.FILESIZE)
      assertThat(orderByDirection).isEqualTo(Direction.ASC)
      assertThat(metadataExact).isEqualTo(this@DocumentSearchByMetadataExactIntTest.metadataJson)
    }
  }

  /**
   * prisonNumber like 56 / expected results 6
   * prisonCode = SFI / expected results 4
   * Total expected 4
   */
  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all documents by partial matching prisonNumber to 56 and exact match prisonCode to SFI`() {
    val metadata: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"${PRISON_NUMBER_PARTIAL_VALUE}\" }")
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonCode\": \"${PRISON_CODE_EXACT_VALUE}\" }")

    val response = webTestClient.searchDocuments(null, metadata, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.metadata["prisonCode"].asString()).isEqualTo(PRISON_CODE_EXACT_VALUE)
        assertThat(it.metadata["prisonNumber"].asString()).contains(PRISON_NUMBER_PARTIAL_VALUE)
        assertThat(it.metadata["prisonNumber"].asString()).isIn(EXPECTED_PRISON_NUMBER_BY_CODE_MAP[PRISON_CODE_EXACT_VALUE])
      }

      assertThat(results.size).isEqualTo(4)
      assertThat(totalResultsCount).isEqualTo(4)
    }
  }

  /**
   * prisonNumber = A1234BC / expected results 3 (1 deleted)
   * document Type HMCTS_WARRANT / expected results 8
   * Total expected 1
   */
  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all documents by document type HMCTS_WARRANT and exact match prisonNumber to A1234BC`() {
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"${PRISON_NUMBER_REQUEST_REPORT}\" }")

    val response = webTestClient.searchDocuments(listOf(DocumentType.HMCTS_WARRANT), null, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo(PRISON_NUMBER_REQUEST_REPORT)
        assertThat(it.documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
      }

      assertThat(results.size).isEqualTo(1)
      assertThat(totalResultsCount).isEqualTo(1)
    }
  }

  /**
   * prisonNumber = A1234BC / expected results 3 (1 deleted)
   * document Type HMCTS_WARRANT / expected results 8
   * court like magistrates / expected 3
   * Total expected 1
   */
  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all documents by document type HMCTS_WARRANT and partial match court to magistrates and exact match prisonNumber to A1234BC`() {
    val metadata: JsonNode = jsonMapper.readTree("{ \"court\": \"maGistrATEs\" }")
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"${PRISON_NUMBER_REQUEST_REPORT}\" }")

    val response = webTestClient.searchDocuments(listOf(DocumentType.HMCTS_WARRANT), metadata, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
        assertThat(it.metadata["court"].asString().lowercase()).contains("magistrates")
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo(PRISON_NUMBER_REQUEST_REPORT)
      }

      assertThat(results.size).isEqualTo(1)
      assertThat(totalResultsCount).isEqualTo(1)
    }
  }

  /**
   * prisonNumber = E5678FG / expected results 2
   * prisonCode = SFI / expected results 4
   * Total expected 2
   */
  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all documents by exact match prisonNumber to E5678FG and prisonCode to SFI`() {
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"E5678FG\", \"prisonCode\": \"${PRISON_CODE_EXACT_VALUE}\" }")

    val response = webTestClient.searchDocuments(null, null, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.metadata["prisonCode"].asString()).isEqualTo(PRISON_CODE_EXACT_VALUE)
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo("E5678FG")
      }

      assertThat(results.size).isEqualTo(2)
      assertThat(totalResultsCount).isEqualTo(2)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all documents by document type PRISON_COURT_REGISTER and exact match prisonNumber to E5678FG and prisonCode to SFI`() {
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"E5678FG\", \"prisonCode\": \"${PRISON_CODE_EXACT_VALUE}\" }")

    val response = webTestClient.searchDocuments(listOf(DocumentType.PRISON_COURT_REGISTER), null, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.documentType).isEqualTo(DocumentType.PRISON_COURT_REGISTER)
        assertThat(it.metadata["prisonCode"].asString()).isEqualTo(PRISON_CODE_EXACT_VALUE)
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo("E5678FG")
      }

      assertThat(results.size).isEqualTo(1)
      assertThat(totalResultsCount).isEqualTo(1)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all documents by document type HMCTS_WARRANT and PRISON_COURT_REGISTER and exact match prisonNumber to E5678FG and prisonCode to SFI`() {
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonNumber\": \"E5678FG\", \"prisonCode\": \"${PRISON_CODE_EXACT_VALUE}\" }")

    val response = webTestClient.searchDocuments(listOf(DocumentType.HMCTS_WARRANT, DocumentType.PRISON_COURT_REGISTER), null, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.documentType).isIn(listOf(DocumentType.HMCTS_WARRANT, DocumentType.PRISON_COURT_REGISTER))
        assertThat(it.metadata["prisonCode"].asString()).isEqualTo(PRISON_CODE_EXACT_VALUE)
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo("E5678FG")
      }

      assertThat(results.size).isEqualTo(2)
      assertThat(totalResultsCount).isEqualTo(2)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search document SUBJECT_ACCESS_REQUEST_REPORT by exact match by prisonCode PVI - client has document type additional role`() {
    val metadataExact: JsonNode = jsonMapper.readTree("{ \"prisonCode\": \"PVI\" }")

    val response = webTestClient.searchDocuments(
      null,
      null,
      roles = listOf(ROLE_DOCUMENT_READER, ROLE_DOCUMENT_TYPE_SAR),
      metadataExact = metadataExact,
    )

    with(response) {
      results.onEach {
        assertThat(it.documentType).isEqualTo(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT)
        assertThat(it.metadata["prisonCode"].asString()).isEqualTo("PVI")
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo(PRISON_NUMBER_REQUEST_REPORT)
      }

      assertThat(results.size).isEqualTo(1)
      assertThat(totalResultsCount).isEqualTo(1)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search all document types by prison number - client does not have document type additional role`() {
    val response = webTestClient.searchDocuments(
      null,
      null,
      roles = listOf(ROLE_DOCUMENT_READER),
      metadataExact = metadataJson,
    )

    with(response) {
      results.onEach {
        assertThat(it.documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
        assertThat(it.metadata["prisonNumber"].asString()).isEqualTo(PRISON_NUMBER_REQUEST_REPORT)
      }

      assertThat(results.size).isEqualTo(1)
      assertThat(totalResultsCount).isEqualTo(1)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @ParameterizedTest
  @CsvSource(
    "Stafford Crown, Stafford Crown, 2",
    "STAFFORD CROWN, Stafford Crown, 2",
    "stafford crown, Stafford Crown, 2",
    "sTaFFoRd croWn, Stafford Crown, 2",
    "Stafford,,0",
    "Stoke on Trent Crown, Stoke on Trent Crown, 1",
    "stoke on trent crown, Stoke on Trent Crown, 1",
  )
  fun `search by metadata exact match court Stafford Crown is case insensitive`(court: String, expected: String?, expectedTotal: Int) {
    val metadataExact = jsonMapper.readTree("{ \"court\": \"${court}\" }")

    val response = webTestClient.searchDocuments(listOf(documentType), null, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.metadata["court"].asString()).isEqualTo(expected)
      }

      assertThat(results.size).isEqualTo(expectedTotal)
      assertThat(totalResultsCount).isEqualTo(expectedTotal.toLong())
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `search string array metadata property`() {
    val metadata = jsonMapper.readTree("{ \"previousPrisonNumbers\": \"${PRISON_NUMBER_REQUEST_REPORT}\" }")

    val response = webTestClient.searchDocuments(listOf(documentType), metadata)

    with(response) {
      results.onEach { document ->
        assertThat(document.metadata["previousPrisonNumbers"][0].asString()).isEqualTo(PRISON_NUMBER_REQUEST_REPORT)
        assertThat(document.metadata["previousPrisonNumbers"].size()).isEqualTo(2)
      }

      assertThat(results.size).isEqualTo(1)
      assertThat(totalResultsCount).isEqualTo(1)
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @ParameterizedTest
  @CsvSource(
    "{ \"previousPrisonCodes\": [\"MDI\"] },,, MDI, 1",
    "{ \"previousPrisonCodes\": [\"MDI\"] }, HMCTS_WARRANT,, MDI, 1",
    "{ \"previousPrisonCodes\": [\"MDI\"] },, { \"prisonNumber\": \"345\" }, MDI, 1",
    "{ \"previousPrisonCodes\": [\"MDI\"] }, HMCTS_WARRANT, { \"prisonNumber\": \"345\" }, MDI, 1",
    "'{ \"previousPrisonCodes\": [\"MDI\"], \"prisonNumber\": \"C3456DE\" }',,, MDI, 1",
    "'{ \"previousPrisonCodes\": [\"MDI\"], \"prisonNumber\": \"B2345CD\" }',,, MDI, 0",
    "{ \"previousPrisonCodes\": [\"mdi\"] },,, MDI, 1",
    "{ \"previousPrisonCodes\": [\"kmi\"] },,, KMI, 2",
    "{ \"previousPrisonCodes\": [\"fake\"] },,, fake, 0",
  )
  fun `search by metadata exact match prisonCode {prisonCode} is in list of previousPrisonCodes, should return {expected} total results`(searchMetadataExact: String, searchDocumentType: DocumentType?, searchMetadata: String?, expected: String, expectedTotal: Int) {
    val documentTypes = if (searchDocumentType != null) {
      listOf(searchDocumentType)
    } else {
      null
    }

    val metadata = if (searchMetadata != null) {
      jsonMapper.readTree(searchMetadata)
    } else {
      null
    }

    val metadataExact = jsonMapper.readTree(searchMetadataExact)

    val response = webTestClient.searchDocuments(documentTypes, metadata, metadataExact = metadataExact)

    with(response) {
      results.onEach {
        assertThat(it.metadata["previousPrisonCodes"]).isNotEmpty()
        assertThat(it.metadata["previousPrisonCodes"].firstOrNull { p -> p.asString().equals(expected) }).isNotNull()
      }

      assertThat(results.size).isEqualTo(expectedTotal)
      assertThat(totalResultsCount).isEqualTo(expectedTotal.toLong())
    }
  }

  @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
  @Test
  fun `search limits results to page size and returns total results count`() {
    val response = webTestClient.searchDocuments(listOf(documentType), null, pageSize = 3, metadataExact = metadataJson)

    with(response) {
      assertThat(results).hasSize(3)
      assertThat(totalResultsCount).isEqualTo(5)
    }
  }

  @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
  @Test
  fun `search skips to second page and returns total results count`() {
    val response = webTestClient.searchDocuments(listOf(documentType), null, page = 1, pageSize = 3, metadataExact = metadataJson)

    with(response) {
      assertThat(results).hasSize(2)
      assertThat(totalResultsCount).isEqualTo(5)
    }
  }

  @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
  @Test
  fun `search returns no results for page out of range`() {
    val response = webTestClient.searchDocuments(listOf(documentType), null, page = 2, pageSize = 3, metadataExact = metadataJson)

    with(response) {
      assertThat(results).isEmpty()
      assertThat(totalResultsCount).isEqualTo(5)
    }
  }

  @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
  @Test
  fun `default ordering is by created time descending`() {
    val response = webTestClient.searchDocuments(null, null, metadataExact = metadataJson)

    assertThat(response.results).containsExactlyElementsOf(
      response.results.sortedByDescending { it.createdTime },
    )
  }

  @Sql("classpath:test_data/document-search-pagination-and-ordering.sql")
  @Test
  fun `order by file size ascending`() {
    val response = webTestClient.searchDocuments(null, null, orderBy = DocumentSearchOrderBy.FILESIZE, orderByDirection = Direction.ASC, metadataExact = metadataJson)

    assertThat(response.results).containsExactlyElementsOf(
      response.results.sortedBy { it.fileSize },
    )
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `audits event`() {
    webTestClient.searchDocuments(listOf(documentType), metadataJson, metadataExact = metadataJson)

    await untilCallTo { auditSqsClient.countMessagesOnQueue(auditQueueUrl).get() } matches { it == 1 }

    val messageBody = auditSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueueUrl).build()).get().messages()[0].body()

    with(objectMapper.readValue<AuditService.AuditEvent>(messageBody)) {
      assertThat(what).isEqualTo(EventType.DOCUMENTS_SEARCHED.name)
      assertThat(whenLocalDateTime()).isCloseTo(LocalDateTime.now(), Assertions.within(3, ChronoUnit.SECONDS))
      assertThat(who).isEqualTo(username)
      assertThat(service).isEqualTo(serviceName)

      with(objectMapper.readValue<DocumentsSearchedEvent>(details)) {
        assertThat(request).isEqualTo(DocumentSearchRequest(listOf(documentType), metadataJson, metadataExact = metadataJson))
        assertThat(resultsCount).isEqualTo(1)
      }
    }
  }

  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `tracks event`() {
    webTestClient.searchDocuments(listOf(documentType), metadataJson, metadataExact = metadataJson)

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENTS_SEARCHED.name), customEventProperties.capture(), customEventMetrics.capture())

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
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(1.0)
      assertThat(this[PAGE_PROPERTY_KEY]).isEqualTo(0.0)
      assertThat(this[PAGE_SIZE_PROPERTY_KEY]).isEqualTo(10.0)
      assertThat(this[RESULTS_COUNT_METRIC_KEY]).isEqualTo(1.0)
      assertThat(this[TOTAL_RESULTS_COUNT_METRIC_KEY]).isEqualTo(1.0)
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
    metadataExact: JsonNode? = null,
  ) = post()
    .uri("/documents/search")
    .bodyValue(DocumentSearchRequest(documentTypes, metadata, page, pageSize, orderBy, orderByDirection, metadataExact = metadataExact))
    .headers(setAuthorisation(roles = roles))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody<DocumentSearchResult>()
    .returnResult().responseBody!!

  private companion object {
    const val PRISON_NUMBER_PARTIAL_VALUE = "56"
    const val PRISON_CODE_EXACT_VALUE = "SFI"
    const val PRISON_NUMBER_REQUEST_REPORT = "A1234BC"
    val EXPECTED_PRISON_NUMBER_BY_CODE_MAP: Map<String, List<String>> = mapOf(
      Pair(PRISON_CODE_EXACT_VALUE, listOf("D4567EF", "E5678FG")),
    )
  }
}
