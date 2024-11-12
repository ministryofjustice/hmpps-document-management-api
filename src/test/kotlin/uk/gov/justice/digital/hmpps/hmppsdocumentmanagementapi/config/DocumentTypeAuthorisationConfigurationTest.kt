package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.servlet.HandlerMapping
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ROLE_DOCUMENT_TYPE_SAR
import java.util.UUID

class DocumentTypeAuthorisationConfigurationTest {
  private val documentRepository = mock<DocumentRepository>()
  private var interceptor = DocumentTypeAuthorisationInterceptor(documentRepository)

  private val req = MockHttpServletRequest()
  private val res = MockHttpServletResponse()

  @Test
  fun `authorised document types contains only types that don't require additional roles when user has no additional roles`() {
    interceptor.preHandle(req, res, "null")

    assertThat(req.authorisedDocumentTypes()).isEqualTo(DocumentType.entries.filter { it.additionalRoles.isEmpty() })
  }

  @ParameterizedTest
  @MethodSource("documentTypesRequiringAdditionalRoleWithRoleMapping")
  fun `authorised document types correct when request has additional document type role`(documentType: DocumentType, role: String) {
    req.addUserRole(role)

    interceptor.preHandle(req, res, "null")

    assertThat(req.authorisedDocumentTypes()).contains(documentType)
  }

  @ParameterizedTest
  @MethodSource("documentTypesRequiringAdditionalRoles")
  fun `document type requiring custom roles from document unique identifier throws exception when request does not have document type role`(documentType: DocumentType) {
    val documentUuid = UUID.randomUUID()

    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentUuid" to documentUuid,
      ),
    )

    whenever(documentRepository.getDocumentTypeByDocumentUuid(documentUuid)).thenReturn(documentType)

    assertThrows<AccessDeniedException>(
      "Document type '${documentType}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  @ParameterizedTest
  @MethodSource("documentTypesRequiringAdditionalRoles")
  fun `document type requiring custom roles from path variable throws exception when request does not have the document type role`(documentType: DocumentType) {
    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentType" to documentType,
      ),
    )

    assertThrows<AccessDeniedException>(
      "Document type '${documentType}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  @ParameterizedTest
  @MethodSource("documentTypesRequiringAdditionalRoles")
  fun `authorisation check prioritises document type from unique identifier over from path variable`(documentType: DocumentType) {
    val documentUuid = UUID.randomUUID()

    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentUuid" to documentUuid,
        "documentType" to DocumentType.HMCTS_WARRANT,
      ),
    )

    whenever(documentRepository.getDocumentTypeByDocumentUuid(documentUuid)).thenReturn(documentType)

    assertThrows<AccessDeniedException>(
      "Document type '${documentType}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  @ParameterizedTest
  @MethodSource("documentTypesRequiringAdditionalRoles")
  fun `authorisation check uses document type from path variable if document type from unique identifier is null`(documentType: DocumentType) {
    val documentUuid = UUID.randomUUID()

    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentUuid" to documentUuid,
        "documentType" to documentType,
      ),
    )

    whenever(documentRepository.getDocumentTypeByDocumentUuid(documentUuid)).thenReturn(null)

    assertThrows<AccessDeniedException>(
      "Document type '${documentType}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  companion object {
    @JvmStatic
    fun documentTypesRequiringAdditionalRoles() = listOf(
      Arguments.of(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT),
      Arguments.of(DocumentType.DISTINGUISHING_MARK_IMAGE)
    )

    @JvmStatic
    fun documentTypesRequiringAdditionalRoleWithRoleMapping() = listOf(
      Arguments.of(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT, ROLE_DOCUMENT_TYPE_SAR),
      Arguments.of(DocumentType.DISTINGUISHING_MARK_IMAGE, ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE)
    )
  }

  private fun HttpServletRequest.authorisedDocumentTypes() =
    (getAttribute(AUTHORISED_DOCUMENT_TYPES) as List<*>).filterIsInstance<DocumentType>()
}
