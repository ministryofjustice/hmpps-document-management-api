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
    val authorisedDocumentTypes = request.authorisedDocumentTypes()
    request.setAttribute(AUTHORISED_DOCUMENT_TYPES, authorisedDocumentTypes)

    val documentType = request.documentTypeFromUuidOrTypePathVariable()

    documentType?.also {
      if (!authorisedDocumentTypes.contains(it)) {
        throw AccessDeniedException("Document type '$it' requires additional role")
      }
    }

    return true
  }

  private fun HttpServletRequest.authorisedDocumentTypes() =
    DocumentType.entries.filter { it.additionalRoles.isEmpty() || it.additionalRoles.any { role -> isUserInRole(role) } }

  private fun HttpServletRequest.pathVariables() =
    getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>? ?: emptyMap<String, String>()

  private fun HttpServletRequest.documentUuidFromPathVariable() =
    pathVariables()["documentUuid"]
      ?.let {
        try {
          UUID.fromString(it.toString())
        } catch (e: IllegalArgumentException) {
          null
        }
      }

  private fun HttpServletRequest.documentTypeFromPathVariable() =
    pathVariables()["documentType"]
      ?.let {
        try {
          DocumentType.valueOf(it.toString())
        } catch (e: IllegalArgumentException) {
          null
        }
      }

  private fun HttpServletRequest.documentTypeFromUuidOrTypePathVariable() =
    documentUuidFromPathVariable()
      ?.let { documentRepository.getDocumentTypeByDocumentUuid(it) }
      ?: documentTypeFromPathVariable()
}
