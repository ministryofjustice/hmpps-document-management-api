package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.core.env.Environment

@ExtendWith(MockitoExtension::class)
class FeatureSwitchesTest {

  @Mock
  private lateinit var environment: Environment

  @Test
  fun `isEnabled should return default value when property is not configured`() {
    val featureSwitches = FeatureSwitches(environment)
    val defaultValue = false

    val result = featureSwitches.isEnabled(Feature.HMPPS_AUDIT, defaultValue)

    assertEquals(defaultValue, result)
  }

  @Test
  fun `isEnabled should return configured value when property is configured`() {
    val featureSwitches = FeatureSwitches(environment)
    val configuredValue = true
    val defaultValue = false

    whenever(environment.getProperty(Feature.HMPPS_AUDIT.label, Boolean::class.javaObjectType)).thenReturn(configuredValue)

    val result = featureSwitches.isEnabled(Feature.HMPPS_AUDIT, defaultValue)

    assertEquals(configuredValue, result)
  }
}
