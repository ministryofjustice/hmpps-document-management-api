package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.CLIENT_ID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.JwtAuthHelper

@Import(JwtAuthHelper::class, ClientTrackingInterceptor::class, ClientTrackingConfiguration::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingConfigurationTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingInterceptor: ClientTrackingInterceptor

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthHelper

  private val res = MockHttpServletResponse()
  private val req = MockHttpServletRequest()

  private val telemetryExtension: OpenTelemetryExtension = OpenTelemetryExtension.create()
  private val tracer: Tracer = telemetryExtension.openTelemetry.getTracer("test")

  @Test
  fun `add user attributes`() {
    val user = "TEST_USER"
    val token = jwtAuthHelper.createJwt(subject = user, user = user, client = null)
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")

    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.preHandle(req, res, "null") }
      end()
    }

    telemetryExtension.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("username"), user)
        it.hasAttribute(AttributeKey.stringKey("enduser.id"), user)
      },)
    },)
  }

  @Test
  fun `add client id attribute`() {
    val token = jwtAuthHelper.createJwt(subject = CLIENT_ID, user = null, client = CLIENT_ID)
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")

    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.preHandle(req, res, "null") }
      end()
    }

    telemetryExtension.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("clientId"), CLIENT_ID)
      },)
    },)
  }
}
