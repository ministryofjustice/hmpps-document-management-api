package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event

import com.fasterxml.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document

data class DocumentMetadataReplacedEvent(
  val document: Document,

  val originalMetadata: JsonNode,
)
