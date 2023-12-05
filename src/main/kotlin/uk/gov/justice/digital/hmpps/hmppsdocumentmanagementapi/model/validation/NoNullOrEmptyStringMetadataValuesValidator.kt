package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

class NoNullOrEmptyStringMetadataValuesValidator : ConstraintValidator<NoNullOrEmptyStringMetadataValues, JsonNode> {
  override fun isValid(value: JsonNode?, context: ConstraintValidatorContext): Boolean {
    if (value == null || !value.isObject) {
      return true
    }

    for (fieldName in value.fieldNames()) {
      if (value[fieldName].isNullOrEmpty()) {
        return false
      }
    }

    return true
  }

  private fun JsonNode?.isNullOrEmpty() =
    this == null || !isTextual || asText().isEmpty()
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NoNullOrEmptyStringMetadataValuesValidator::class])
annotation class NoNullOrEmptyStringMetadataValues(
  val message: String = "Metadata property values must be non null or empty strings.",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)
