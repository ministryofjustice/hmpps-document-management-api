package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID

const val AUTHORISED_DOCUMENT_TYPES = "Authorised-Document-Types"

@Configuration
class DocumentTypeAuthorisationConfiguration(private val documentTypeAuthorisationInterceptor: DocumentTypeAuthorisationInterceptor) : WebMvcConfigurer {
  override fun addInterceptors(registry: InterceptorRegistry) {
    log.info("Adding document type authorisation interceptor")
    registry.addInterceptor(documentTypeAuthorisationInterceptor).addPathPatterns("/documents/**")
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

@Configuration
class DocumentTypeAuthorisationInterceptor(
  private val documentRepository: DocumentRepository,
) : HandlerInterceptor {
  override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
    val authorisedDocumentTypes = DocumentType.entries.filter { it.additionalRoles.isEmpty() || it.additionalRoles.any { role -> request.isUserInRole(role) } }
    request.setAttribute(AUTHORISED_DOCUMENT_TYPES, authorisedDocumentTypes)

    val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>

    val documentType = pathVariables["documentUuid"]
      ?.let { try { UUID.fromString(it.toString()) } catch (e: IllegalArgumentException) { null } }
      ?.let { documentRepository.getDocumentTypeByDocumentUuid(it) }
      ?: pathVariables["documentType"]?.let { try { DocumentType.valueOf(it.toString()) } catch (e: IllegalArgumentException) { null } }

    documentType?.also {
      if (!authorisedDocumentTypes.contains(documentType)) {
        throw AccessDeniedException("Document type '$documentType' requires additional role")
      }
    }

    return true
  }
}
