package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

fun DocumentModel.toCustomEventProperties(documentRequestContext: DocumentRequestContext) =
  mapOf(
    SERVICE_NAME_PROPERTY_KEY to documentRequestContext.serviceName,
    USERNAME_PROPERTY_KEY to (documentRequestContext.username ?: ""),
    DOCUMENT_UUID_PROPERTY_KEY to documentUuid.toString(),
    DOCUMENT_TYPE_PROPERTY_KEY to documentType.name,
    DOCUMENT_TYPE_DESCRIPTION_PROPERTY_KEY to documentType.description,
    FILE_EXTENSION_PROPERTY_KEY to fileExtension,
    MIME_TYPE_PROPERTY_KEY to mimeType,
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