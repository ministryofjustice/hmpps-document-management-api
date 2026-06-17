package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchByUuidsRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentSearchedByUuidsEvent
import java.util.UUID

class TelemetryDocumentSearchedByUuidsEventTest {

  private val documentRequestContext = DocumentRequestContext(
    "Service name",
    "LPI",
    "USERNAME",
  )
  private val eventTimeMs = 100L

  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `document search by UUIDs event to custom event properties`(documentUuids: Collection<UUID>, expectedUuids: String) {
    val documentSearchRequest = DocumentSearchByUuidsRequest(documentUuids)
    val event = DocumentSearchedByUuidsEvent(documentSearchRequest, documentUuids.size)

    with(event.toCustomEventProperties(documentRequestContext)) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(documentRequestContext.activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(expectedUuids)
    }
  }

  @Test
  fun `documents search by UUIDs event to custom event properties check empty context variables`() {
    val documentSearchRequest = DocumentSearchByUuidsRequest(listOf())
    val event = DocumentSearchedByUuidsEvent(documentSearchRequest, 0)
    val emptyContext = DocumentRequestContext("Service name", null, null)

    with(event.toCustomEventProperties(emptyContext)) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo("Service name")
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo("")
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo("")
    }
  }

  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `documents searched by UUIDs event to custom event metrics`(documentUuids: Collection<UUID>) {
    val documentSearchRequest = DocumentSearchByUuidsRequest(documentUuids)
    val expectedTotal = documentUuids.size
    val event = DocumentSearchedByUuidsEvent(documentSearchRequest, expectedTotal)

    with(event.toCustomEventMetrics(eventTimeMs)) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isEqualTo(eventTimeMs.toDouble())
      assertThat(this[RESULTS_COUNT_METRIC_KEY]).isEqualTo(event.resultsCount.toDouble())
      assertThat(this[RESULTS_COUNT_METRIC_KEY]).isEqualTo(expectedTotal.toDouble())
      assertThat(this[TOTAL_RESULTS_COUNT_METRIC_KEY]).isEqualTo(event.resultsCount.toDouble())
      assertThat(this[TOTAL_RESULTS_COUNT_METRIC_KEY]).isEqualTo(expectedTotal.toDouble())
    }
  }

  companion object {
    @JvmStatic
    fun documentSearchByUuidsTestParameters() = listOf(
      Arguments.of(listOf<UUID>(), ""),
      Arguments.of(listOf(UUID.fromString("4fd5f7b0-eebf-4b69-9489-0cc48550e03b")), "4fd5f7b0-eebf-4b69-9489-0cc48550e03b"),
      Arguments.of(listOf(UUID.fromString("4fd5f7b0-eebf-4b69-9489-0cc48550e03b"), UUID.fromString("8980c409-465c-41a4-969d-affe0d9b9df7")), "4fd5f7b0-eebf-4b69-9489-0cc48550e03b, 8980c409-465c-41a4-969d-affe0d9b9df7"),
    )
  }
}
