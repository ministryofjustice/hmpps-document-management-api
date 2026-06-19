package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFindByUuidsRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.DOCUMENT_UUID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.TOTAL_RESULTS_COUNT_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY

data class DocumentRetrievedByUuidsEvent(
  val request: DocumentFindByUuidsRequest,
  val resultsCount: Int,
) {
  fun toCustomEventProperties(documentRequestContext: DocumentRequestContext) = mapOf(
    SERVICE_NAME_PROPERTY_KEY to documentRequestContext.serviceName,
    ACTIVE_CASE_LOAD_ID_PROPERTY_KEY to (documentRequestContext.activeCaseLoadId ?: ""),
    USERNAME_PROPERTY_KEY to (documentRequestContext.username ?: ""),
    DOCUMENT_UUID_PROPERTY_KEY to (request.documentUuids.joinToString { it.toString() }),
  )

  fun toCustomEventMetrics(eventTimeMs: Long) = mapOf(
    EVENT_TIME_MS_METRIC_KEY to eventTimeMs.toDouble(),
    RESULTS_COUNT_METRIC_KEY to resultsCount.toDouble(),
    TOTAL_RESULTS_COUNT_METRIC_KEY to resultsCount.toDouble(),
  )
}
