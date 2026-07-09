package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.util.stream.Stream

class ObjectNodeExtensionTest {

  private val mapper = ObjectMapper()

  @ParameterizedTest(name = "{0}")
  @MethodSource("mergeArguments")
  fun `should merge object nodes`(
    @Suppress("UNUSED_PARAMETER") description: String,
    existing: String,
    update: String,
    expected: String,
  ) {
    val existingNode = mapper.readTree(existing) as ObjectNode
    val updateNode = mapper.readTree(update) as ObjectNode
    val expectedNode = mapper.readTree(expected)

    val result = existingNode.merge(updateNode)

    assertThat(expectedNode).isEqualTo(result)

    // Ensure neither input was modified
    assertThat(mapper.readTree(existing)).isEqualTo(existingNode)
    assertThat(mapper.readTree(update)).isEqualTo(updateNode)

    // Ensure a new instance was returned
    assertThat(existingNode).isNotEqualTo(result)
  }

  companion object {
    @JvmStatic
    fun mergeArguments(): Stream<Arguments> = Stream.of(
      Arguments.of(
        "adds new field",
        """{"a":1}""",
        """{"b":2}""",
        """{"a":1,"b":2}""",
      ),
      Arguments.of(
        "overwrites primitive",
        """{"a":1}""",
        """{"a":2}""",
        """{"a":2}""",
      ),
      Arguments.of(
        "does not deep merge nested objects",
        """{"a":{"x":1,"y":2}}""",
        """{"a":{"y":3,"z":4}}""",
        """{"a":{"y":3,"z":4}}""",
      ),
      Arguments.of(
        "replaces object with primitive",
        """{"a":{"x":1}}""",
        """{"a":42}""",
        """{"a":42}""",
      ),
      Arguments.of(
        "replaces primitive with object",
        """{"a":42}""",
        """{"a":{"x":1}}""",
        """{"a":{"x":1}}""",
      ),
      Arguments.of(
        "replaces arrays",
        """{"a":[1,2]}""",
        """{"a":[3]}""",
        """{"a":[3]}""",
      ),
    )
  }
}
