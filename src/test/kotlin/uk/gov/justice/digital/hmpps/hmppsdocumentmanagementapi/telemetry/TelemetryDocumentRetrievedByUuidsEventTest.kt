package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentRetrievedByUuidsEvent
import java.util.UUID

class TelemetryDocumentRetrievedByUuidsEventTest {

  private val documentRequestContext = DocumentRequestContext(
    "Service name",
    "LPI",
    "USERNAME",
  )
  private val eventTimeMs = 100L

  @ParameterizedTest
  @MethodSource("documentRetrievedByUuidsEventTestParameters")
  fun `document retrieved by UUIDs event to custom event properties`(documentUuids: Collection<UUID>, expectedUuids: String) {
    val event = DocumentRetrievedByUuidsEvent(documentUuids, documentUuids.size)

    with(event.toCustomEventProperties(documentRequestContext)) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(documentRequestContext.activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(documentRequestContext.username)
      assertThat(this[DOCUMENT_UUID_PROPERTY_KEY]).isEqualTo(expectedUuids)
    }
  }

  @Test
  fun `test event custom event properties when empty context variables, should contain empty properties`() {
    val event = DocumentRetrievedByUuidsEvent(emptyList(), 0)
    val emptyContext = DocumentRequestContext("Service name", null, null)

    with(event.toCustomEventProperties(emptyContext)) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo("Service name")
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo("")
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo("")
    }
  }

  @ParameterizedTest
  @MethodSource("documentRetrievedByUuidsEventTestParameters")
  fun `test event custom event metrics, should contain cound and time metrics properties as in event`(documentUuids: Collection<UUID>) {
    val expectedTotal = documentUuids.size
    val event = DocumentRetrievedByUuidsEvent(documentUuids, expectedTotal)

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
    fun documentRetrievedByUuidsEventTestParameters() = listOf(
      Arguments.of(listOf<UUID>(), ""),
      Arguments.of(listOf(UUID.fromString("4fd5f7b0-eebf-4b69-9489-0cc48550e03b")), "4fd5f7b0-eebf-4b69-9489-0cc48550e03b"),
      Arguments.of(listOf(UUID.fromString("4fd5f7b0-eebf-4b69-9489-0cc48550e03b"), UUID.fromString("8980c409-465c-41a4-969d-affe0d9b9df7")), "4fd5f7b0-eebf-4b69-9489-0cc48550e03b, 8980c409-465c-41a4-969d-affe0d9b9df7"),
    )
  }
}
