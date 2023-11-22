package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.SERVICE_NAME
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.USERNAME

@Configuration
class DocumentRequestContextConfiguration(private val documentRequestContextInterceptor: DocumentRequestContextInterceptor) : WebMvcConfigurer {
  override fun addInterceptors(registry: InterceptorRegistry) {
    log.info("Adding document request context interceptor")
    registry.addInterceptor(documentRequestContextInterceptor).addPathPatterns("/documents/**")
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

@Configuration
class DocumentRequestContextInterceptor : HandlerInterceptor {
  override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
    val serviceName = request.getHeader(SERVICE_NAME)?.trim()
    require(!serviceName.isNullOrEmpty()) {
      "$SERVICE_NAME header is required"
    }
    val username = request.getHeader(USERNAME)?.trim()?.takeUnless(String::isBlank)

    request.setAttribute(DocumentRequestContext::class.simpleName, DocumentRequestContext(serviceName, username))

    return true
  }
}
