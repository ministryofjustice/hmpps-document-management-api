package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID

class DocumentFindByUuidsRequestTest {
  private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `valid request - list of document UUIDs is provided`(documentUuidListSize: Int) {
    val documentUuids = ArrayList(List(documentUuidListSize) { UUID.randomUUID() })

    val request = DocumentFindByUuidsRequest(documentUuids)

    assertThat(validator.validate(request)).isEmpty()
  }

  companion object {
    @JvmStatic
    fun documentSearchByUuidsTestParameters() = listOf(
      Arguments.of(0),
      Arguments.of(1),
      Arguments.of(3),
    )
  }
}
