package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

fun ObjectNode.merge(updates: JsonNode): ObjectNode {
  val existing = this.deepCopy()
  for ((name, updateValue) in updates.properties()) {
    existing.set(name, updateValue)
  }

  return existing
}
