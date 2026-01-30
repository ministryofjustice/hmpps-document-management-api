package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsScannedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class TelemetryTransformFunctionsTest {
  private val document = DocumentModel(
    documentUuid = UUID.randomUUID(),
    documentType = DocumentType.HMCTS_WARRANT,
    documentFilename = "warrant_for_sentencing.pdf",
    filename = "warrant_for_sentencing",
    fileExtension = "pdf",
    fileSize = 3876,
    fileHash = "d58e3582afa99040e27b92b13c8f2280",
    mimeType = "application/pdf",
    metadata = ObjectMapper().readTree("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\", \"court\": \"Stafford Crown\", \"warrantDate\": \"2021-09-27\" }"),
    createdTime = LocalDateTime.now(),
    createdByServiceName = "Remand and sentencing",
    createdByUsername = "CREATED_BY_USERNAME",
  )

  private val documentRequestContext = DocumentRequestContext(
    "Service name",
    "LPI",
    "USERNAME",
  )

  private val eventTimeMs = 100L

  @Test
  fun `document model to custom event properties`() {
    with(document.toCustomEventProperties(documentRequestContext)) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(documentRequestContext.activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(document.documentUuid.toString())
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(document.documentType.name)
      assertThat(this[DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY]).isEqualTo(document.documentType.description)
      assertThat(this[FILE_EXTENSION_PROPERTY_KEY]).isEqualTo(document.fileExtension)
      assertThat(this[MIME_TYPE_PROPERTY_KEY]).isEqualTo(document.mimeType)
    }
  }

  @Test
  fun `document model to custom event properties no active case load id`() {
    val eventProperties = document.toCustomEventProperties(DocumentRequestContext("Service name", null, null))
    assertThat(eventProperties[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo("")
  }

  @Test
  fun `document model to custom event properties no username`() {
    val eventProperties = document.toCustomEventProperties(DocumentRequestContext("Service name", null, null))
    assertThat(eventProperties[USERNAME_PROPERTY_KEY]).isEqualTo("")
  }

  @Test
  fun `documents search event to custom event properties`() {
    val documentSearchRequest = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }"))
    val event = DocumentsSearchedEvent(documentSearchRequest, 10, 13)
    with(event.toCustomEventProperties(documentRequestContext)) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(documentRequestContext.activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.username)
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo(documentSearchRequest.documentType!!.name)
      assertThat(this[DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY]).isEqualTo(documentSearchRequest.documentType!!.description)
      assertThat(this[ORDER_BY_PROPERTY_KEY]).isEqualTo(documentSearchRequest.orderBy.name)
      assertThat(this[ORDER_BY_DIRECTION_PROPERTY_KEY]).isEqualTo(documentSearchRequest.orderByDirection.name)
    }
  }

  @Test
  fun `documents search event to custom event properties no active case load id`() {
    val documentSearchRequest = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }"))
    val event = DocumentsSearchedEvent(documentSearchRequest, 10, 13)
    val eventProperties = event.toCustomEventProperties(DocumentRequestContext("Service name", null, null))
    assertThat(eventProperties[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo("")
  }

  @Test
  fun `documents search event to custom event properties no username`() {
    val documentSearchRequest = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }"))
    val event = DocumentsSearchedEvent(documentSearchRequest, 10, 13)
    val eventProperties = event.toCustomEventProperties(DocumentRequestContext("Service name", null, null))
    assertThat(eventProperties[USERNAME_PROPERTY_KEY]).isEqualTo("")
  }

  @Test
  fun `documents search event to custom event properties no document type`() {
    val documentSearchRequest = DocumentSearchRequest(null, ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }"))
    val event = DocumentsSearchedEvent(documentSearchRequest, 10, 13)
    with(event.toCustomEventProperties(documentRequestContext)) {
      assertThat(this[DOCUMENT_TYPE_PROPERTY_KEY]).isEqualTo("")
      assertThat(this[DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY]).isEqualTo("")
    }
  }

  @Test
  fun `document scanned event to custom event properties with no username`() {
    val documentRequestContext = DocumentRequestContext(
      "Service name",
      "LPI",
      null,
    )
    val event = DocumentsScannedEvent(documentRequestContext, document.fileSize)
    with(event.toCustomEventProperties()) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(documentRequestContext.activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo("")
    }
  }

  @Test
  fun `document scanned event to custom event properties with no caseload`() {
    val documentRequestContext = DocumentRequestContext(
      "Service name",
      null,
      "USERNAME",
    )
    val event = DocumentsScannedEvent(documentRequestContext, document.fileSize)
    with(event.toCustomEventProperties()) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo("")
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.username)
    }
  }

  @Test
  fun `document model to custom event metrics`() {
    with(document.toCustomEventMetrics(eventTimeMs)) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isEqualTo(eventTimeMs.toDouble())
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(document.fileSize.toDouble())
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(4.0)
    }
  }

  @Test
  fun `document metadata replaced event to custom event metrics`() {
    val documentMetadataReplacedEvent = DocumentMetadataReplacedEvent(
      document,
      ObjectMapper().readTree("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"),
    )
    with(documentMetadataReplacedEvent.toCustomEventMetrics(eventTimeMs)) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isEqualTo(eventTimeMs.toDouble())
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(document.fileSize.toDouble())
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(4.0)
      assertThat(this[ORIGINAL_METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(2.0)
    }
  }

  @Test
  fun `documents searched event to custom event metrics`() {
    val documentSearchRequest = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }"))
    val event = DocumentsSearchedEvent(documentSearchRequest, 10, 13)
    with(event.toCustomEventMetrics(eventTimeMs)) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isEqualTo(eventTimeMs.toDouble())
      assertThat(this[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(1.0)
      assertThat(this[PAGE_PROPERTY_KEY]).isEqualTo(documentSearchRequest.page.toDouble())
      assertThat(this[PAGE_SIZE_PROPERTY_KEY]).isEqualTo(documentSearchRequest.pageSize.toDouble())
      assertThat(this[RESULTS_COUNT_METRIC_KEY]).isEqualTo(event.resultsCount.toDouble())
      assertThat(this[TOTAL_RESULTS_COUNT_METRIC_KEY]).isEqualTo(event.totalResultsCount.toDouble())
    }
  }

  @Test
  fun `documents searched event to custom event metrics no metadata`() {
    val documentSearchRequest = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, null)
    val event = DocumentsSearchedEvent(documentSearchRequest, 10, 13)
    val eventProperties = event.toCustomEventMetrics(eventTimeMs)
    assertThat(eventProperties[METADATA_FIELD_COUNT_METRIC_KEY]).isEqualTo(0.0)
  }

  @Test
  fun `document scanned event to custom event metrics`() {
    val event = DocumentsScannedEvent(documentRequestContext, document.fileSize)
    with(event.toCustomEventMetrics(eventTimeMs)) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isEqualTo(eventTimeMs.toDouble())
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(document.fileSize.toDouble())
    }
  }
}
