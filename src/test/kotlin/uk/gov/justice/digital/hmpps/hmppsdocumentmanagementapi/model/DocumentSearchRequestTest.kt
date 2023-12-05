package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

class DocumentSearchRequestTest {
  private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

  @Test
  fun `valid request - document type specified null metadata`() {
    val request = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, null)
    assertThat(validator.validate(request)).isEmpty()
  }

  @Test
  fun `valid request - document type specified empty metadata`() {
    val request = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, JacksonUtil.toJsonNode("{}"))
    assertThat(validator.validate(request)).isEmpty()
  }

  @Test
  fun `valid request - document type specified non object metadata`() {
    val request = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, JacksonUtil.toJsonNode("[ \"test\" ]"))
    assertThat(validator.validate(request)).isEmpty()
  }

  @Test
  fun `valid request - null document type valid metadata`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }"))
    assertThat(validator.validate(request)).isEmpty()
  }

  @Test
  fun `document type or metadata criteria must be supplied - null document type and metadata`() {
    val request = DocumentSearchRequest(null, null)
    validator.validate(request).assertSingleValidationError("", "Document type or metadata criteria must be supplied.")
  }

  @Test
  fun `document type or metadata criteria must be supplied - null document type and empty metadata`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("{}"))
    validator.validate(request).assertSingleValidationError("", "Document type or metadata criteria must be supplied.")
  }

  @Test
  fun `document type or metadata criteria must be supplied - null document type and non object metadata`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("[ \"test\" ]"))
    validator.validate(request).assertSingleValidationError("", "Document type or metadata criteria must be supplied.")
  }

  @Test
  fun `metadata property values must be non null or empty strings - null value`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("{ \"prisonNumber\": null }"))
    validator.validate(request).assertSingleValidationError("metadata", "Metadata property values must be non null or empty strings.")
  }

  @Test
  fun `metadata property values must be non null or empty strings - empty value`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("{ \"prisonNumber\": \"\" }"))
    validator.validate(request).assertSingleValidationError("metadata", "Metadata property values must be non null or empty strings.")
  }

  @Test
  fun `metadata property values must be non null or empty strings - numerical value`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("{ \"prisonNumber\": 1234 }"))
    validator.validate(request).assertSingleValidationError("metadata", "Metadata property values must be non null or empty strings.")
  }

  @Test
  fun `metadata property values must be non null or empty strings - decimal value`() {
    val request = DocumentSearchRequest(null, JacksonUtil.toJsonNode("{ \"prisonNumber\": 12.34 }"))
    validator.validate(request).assertSingleValidationError("metadata", "Metadata property values must be non null or empty strings.")
  }

  private fun MutableSet<ConstraintViolation<DocumentSearchRequest>>.assertSingleValidationError(propertyName: String, message: String) =
    with(single()) {
      assertThat(propertyPath.toString()).isEqualTo(propertyName)
      assertThat(message).isEqualTo(message)
    }
}
