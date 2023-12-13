package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

fun DocumentModel.toCustomEventProperties(documentRequestContext: DocumentRequestContext) =
  mapOf(
    SERVICE_NAME_PROPERTY_KEY to documentRequestContext.serviceName,
    ACTIVE_CASE_LOAD_ID_PROPERTY_KEY to (documentRequestContext.activeCaseLoadId ?: ""),
    USERNAME_PROPERTY_KEY to (documentRequestContext.username ?: ""),
    DOCUMENT_UUID_PROPERTY_KEY to documentUuid.toString(),
    DOCUMENT_TYPE_PROPERTY_KEY to documentType.name,
    DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY to documentType.description,
    FILE_EXTENSION_PROPERTY_KEY to fileExtension,
    MIME_TYPE_PROPERTY_KEY to mimeType,
  )

fun DocumentsSearchedEvent.toCustomEventProperties(documentRequestContext: DocumentRequestContext) =
  mapOf(
    SERVICE_NAME_PROPERTY_KEY to documentRequestContext.serviceName,
    ACTIVE_CASE_LOAD_ID_PROPERTY_KEY to (documentRequestContext.activeCaseLoadId ?: ""),
    USERNAME_PROPERTY_KEY to (documentRequestContext.username ?: ""),
    DOCUMENT_TYPE_PROPERTY_KEY to (request.documentType?.name ?: ""),
    DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY to (request.documentType?.description ?: ""),
    ORDER_BY_PROPERTY_KEY to request.orderBy.name,
    ORDER_BY_DIRECTION_PROPERTY_KEY to request.orderByDirection.name,
  )

fun DocumentModel.toCustomEventMetrics(eventTimeMs: Long) =
  mapOf(
    EVENT_TIME_MS_METRIC_KEY to eventTimeMs.toDouble(),
    FILE_SIZE_METRIC_KEY to fileSize.toDouble(),
    METADATA_FIELD_COUNT_METRIC_KEY to metadata.size().toDouble(),
  )

fun DocumentMetadataReplacedEvent.toCustomEventMetrics(eventTimeMs: Long) =
  mapOf(
    EVENT_TIME_MS_METRIC_KEY to eventTimeMs.toDouble(),
    FILE_SIZE_METRIC_KEY to document.fileSize.toDouble(),
    METADATA_FIELD_COUNT_METRIC_KEY to document.metadata.size().toDouble(),
    ORIGINAL_METADATA_FIELD_COUNT_METRIC_KEY to originalMetadata.size().toDouble(),
  )

fun DocumentsSearchedEvent.toCustomEventMetrics(eventTimeMs: Long) =
  mapOf(
    EVENT_TIME_MS_METRIC_KEY to eventTimeMs.toDouble(),
    METADATA_FIELD_COUNT_METRIC_KEY to (request.metadata?.size()?.toDouble() ?: 0.0),
    PAGE_PROPERTY_KEY to request.page.toDouble(),
    PAGE_SIZE_PROPERTY_KEY to request.pageSize.toDouble(),
    RESULTS_COUNT_METRIC_KEY to resultsCount.toDouble(),
    TOTAL_RESULTS_COUNT_METRIC_KEY to totalResultsCount.toDouble(),
  )
