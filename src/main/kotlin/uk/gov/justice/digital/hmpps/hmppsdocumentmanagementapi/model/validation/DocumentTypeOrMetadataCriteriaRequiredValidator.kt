package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.validation

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import kotlin.reflect.KClass

class DocumentTypeOrMetadataCriteriaRequiredValidator : ConstraintValidator<DocumentTypeOrMetadataCriteriaRequired, DocumentSearchRequest> {
  override fun isValid(value: DocumentSearchRequest, context: ConstraintValidatorContext): Boolean {
    if (value.documentType != null) {
      return true
    }

    if (value.metadata.isNotNullWithAtLeastOneProperty()) {
      return true
    }

    return false
  }

  private fun JsonNode?.isNotNullWithAtLeastOneProperty() =
    this != null && this.isObject && this.size() > 0
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DocumentTypeOrMetadataCriteriaRequiredValidator::class])
annotation class DocumentTypeOrMetadataCriteriaRequired(
  val message: String = "Document type or metadata criteria must be supplied.",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)
