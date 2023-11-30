package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.JwtAuthHelper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.SERVICE_NAME
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.USERNAME

@Import(JwtAuthHelper::class, DocumentRequestContextInterceptor::class, DocumentRequestContextConfiguration::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class DocumentRequestContextConfigurationTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var interceptor: DocumentRequestContextInterceptor

  private val req = MockHttpServletRequest()
  private val res = MockHttpServletResponse()

  private val serviceName = "Test Service Name"
  private val username = "TEST_USER"

  @Test
  fun `missing service name throws exception`() {
    assertThrows<IllegalArgumentException>("$SERVICE_NAME header is required") {
      interceptor.preHandle(req, res, "null")
    }
  }

  @Test
  fun `empty service name throws exception`() {
    req.addHeader(SERVICE_NAME, "")

    assertThrows<IllegalArgumentException>("$SERVICE_NAME header is required") {
      interceptor.preHandle(req, res, "null")
    }
  }

  @Test
  fun `whitespace service name throws exception`() {
    req.addHeader(SERVICE_NAME, "    ")

    assertThrows<IllegalArgumentException>("$SERVICE_NAME header is required") {
      interceptor.preHandle(req, res, "null")
    }
  }

  @Test
  fun `populate document request context`() {
    req.addHeader(SERVICE_NAME, serviceName)
    req.addHeader(USERNAME, username)

    interceptor.preHandle(req, res, "null")

    val documentRequestContext = req.getAttribute(DocumentRequestContext::class.simpleName!!) as DocumentRequestContext

    assertThat(documentRequestContext.serviceName).isEqualTo(serviceName)
    assertThat(documentRequestContext.username).isEqualTo(username)
  }

  @Test
  fun `service name is trimmed`() {
    req.addHeader(SERVICE_NAME, " $serviceName  ")

    interceptor.preHandle(req, res, "null")

    val documentRequestContext = req.getAttribute(DocumentRequestContext::class.simpleName!!) as DocumentRequestContext

    assertThat(documentRequestContext.serviceName).isEqualTo(serviceName)
  }

  @Test
  fun `username is optional`() {
    req.addHeader(SERVICE_NAME, serviceName)

    interceptor.preHandle(req, res, "null")

    val documentRequestContext = req.getAttribute(DocumentRequestContext::class.simpleName!!) as DocumentRequestContext

    assertThat(documentRequestContext.username).isNull()
  }

  @Test
  fun `empty username converted to null`() {
    req.addHeader(SERVICE_NAME, serviceName)
    req.addHeader(USERNAME, "")

    interceptor.preHandle(req, res, "null")

    val documentRequestContext = req.getAttribute(DocumentRequestContext::class.simpleName!!) as DocumentRequestContext

    assertThat(documentRequestContext.username).isNull()
  }

  @Test
  fun `whitespace username converted to null`() {
    req.addHeader(SERVICE_NAME, serviceName)
    req.addHeader(USERNAME, "    ")

    interceptor.preHandle(req, res, "null")

    val documentRequestContext = req.getAttribute(DocumentRequestContext::class.simpleName!!) as DocumentRequestContext

    assertThat(documentRequestContext.username).isNull()
  }

  @Test
  fun `username is trimmed`() {
    req.addHeader(SERVICE_NAME, serviceName)
    req.addHeader(USERNAME, " $username  ")

    interceptor.preHandle(req, res, "null")

    val documentRequestContext = req.getAttribute(DocumentRequestContext::class.simpleName!!) as DocumentRequestContext

    assertThat(documentRequestContext.username).isEqualTo(username)
  }
}
