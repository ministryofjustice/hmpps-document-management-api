package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event

import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document

data class DocumentMetadataReplacedEvent(
  val document: Document,

  val originalMetadata: JsonNode,
)
