package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

class DocumentSearchRequestMetadataExactTest {
  private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

  @ParameterizedTest
  @CsvSource(
    "{}, HMCTS_WARRANT,",
    "{},,{ \"prisonNumber\": \"A1234BC\" }",
    "{}, HMCTS_WARRANT,{ \"prisonNumber\": \"A1234BC\" }",
    "[ \"test\" ], HMCTS_WARRANT,",
    "[ \"test\" ],,{ \"prisonNumber\": \"A1234BC\" }",
    "[ \"test\" ], HMCTS_WARRANT,{ \"prisonNumber\": \"A1234BC\" }",
  )
  fun `valid request - with valid document type specified and-or metadata specified, and empty metadata-exact`(metadataExactJson: String, documentType: DocumentType?, metadataJson: String?) {
    val documentTypes = if (documentType != null) listOf(documentType) else null
    val metadata = if (metadataJson != null) ObjectMapper().readTree(metadataJson) else null
    val metadataExact = ObjectMapper().readTree(metadataExactJson)

    val request = DocumentSearchRequest(documentTypes, metadata, metadataExact = metadataExact)
    assertThat(validator.validate(request)).isEmpty()
  }

  @ParameterizedTest
  @CsvSource(
    "{ \"prisonNumber\": \"A1234BC\" },,",
    "{ \"prisonNumber\": \"A1234BC\" }, HMCTS_WARRANT,",
    "{ \"prisonNumber\": \"A1234BC\" },, { \"prisonNumber\": \"A1234BC\" }",
    "{ \"prisonNumber\": \"A1234BC\" }, HMCTS_WARRANT,{ \"prisonNumber\": \"A1234BC\" }",
    "{ \"prisonNumbers\": [ \"A1234BC\" ] },,",
    "{ \"prisonNumbers\": [ \"A1234BC\" ] }, HMCTS_WARRANT,",
    "{ \"prisonNumbers\": [ \"A1234BC\" ] },, { \"prisonNumber\": \"A1234BC\" }",
    "{ \"prisonNumbers\": [ \"A1234BC\" ] }, HMCTS_WARRANT, { \"prisonNumber\": \"A1234BC\" }",
    "'{ \"prisonNumber\": \"A1234BC\", \"prisonNumbers\": [ \"A1234BC\" ] }',,",
    "'{ \"prisonNumber\": \"A1234BC\", \"prisonNumbers\": [ \"A1234BC\" ] }', HMCTS_WARRANT,",
    "'{ \"prisonNumber\": \"A1234BC\", \"prisonNumbers\": [ \"A1234BC\" ] }',, { \"prisonNumber\": \"A1234BC\" }",
    "'{ \"prisonNumber\": \"A1234BC\", \"prisonNumbers\": [ \"A1234BC\" ] }', HMCTS_WARRANT, { \"prisonNumber\": \"A1234BC\" }",
  )
  fun `valid request - with valid metadata-exact, and document type or metadata valid of not specified`(metadataExactJson: String, documentType: DocumentType?, metadataJson: String?) {
    val documentTypes = if (documentType != null) listOf(documentType) else null
    val metadata = if (metadataJson != null) ObjectMapper().readTree(metadataJson) else null
    val metadataExact = ObjectMapper().readTree(metadataExactJson)

    val request = DocumentSearchRequest(documentTypes, metadata, metadataExact = metadataExact)
    assertThat(validator.validate(request)).isEmpty()
  }

  @ParameterizedTest
  @CsvSource(
    "{},'',Document type or metadata criteria must be supplied.",
    "[ \"test\" ],'',Document type or metadata criteria must be supplied.",
    "{ \"prisonNumber\": null },metadataExact,Metadata property values must be non null or empty strings.",
    "{ \"prisonNumber\": \"\" },metadataExact,Metadata property values must be non null or empty strings.",
    "{ \"prisonNumber\": 1234 },metadataExact,Metadata property values must be non null or empty strings.",
    "{ \"prisonNumber\": 12.34 },metadataExact,Metadata property values must be non null or empty strings.",
    "{ \"prisonNumbers\": [ ] },metadataExact,Metadata property values must be non null or empty strings.",
    "{ \"prisonNumbers\": [ \"\" ] },metadataExact,Metadata property values must be non null or empty strings.",
    "'{ \"prisonNumbers\": [ \"1\", \"2\" ] }',metadataExact,Metadata property values must be non null or empty strings.",
  )
  fun `document type or metadata criteria must be supplied - null document type and empty metadata`(metadataExactJson: String, propertyName: String, expected: String) {
    val metadataExact = ObjectMapper().readTree(metadataExactJson)

    val request = DocumentSearchRequest(null, null, metadataExact = metadataExact)
    validator.validate(request).assertSingleValidationError(propertyName, expected)
  }

  private fun MutableSet<ConstraintViolation<DocumentSearchRequest>>.assertSingleValidationError(propertyName: String?, message: String) = with(single()) {
    assertThat(propertyPath.toString()).isEqualTo(propertyName)
    assertThat(this.message).isEqualTo(message)
  }
}
