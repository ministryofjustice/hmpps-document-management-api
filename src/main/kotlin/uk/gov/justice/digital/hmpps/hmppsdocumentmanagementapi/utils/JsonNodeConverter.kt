package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Converter(autoApply = true)
class JsonNodeConverter : AttributeConverter<JsonNode, String> {

  // Create a private instance of the NEW Jackson 3 Mapper
  private val jsonMapper = ObjectMapper()

  override fun convertToDatabaseColumn(attribute: JsonNode?): String? {
    if (attribute == null) {
      return null
    }
    try {
      return jsonMapper.writeValueAsString(attribute)
    } catch (e: JacksonException) {
      throw IllegalArgumentException("Error converting JsonNode to String", e)
    }
  }

  override fun convertToEntityAttribute(dbData: String?): JsonNode? {
    if (dbData.isNullOrEmpty()) {
      return null
    }
    try {
      return jsonMapper.readTree(dbData)
    } catch (e: JacksonException) {
      throw IllegalArgumentException("Error converting String to JsonNode", e)
    }
  }
}
