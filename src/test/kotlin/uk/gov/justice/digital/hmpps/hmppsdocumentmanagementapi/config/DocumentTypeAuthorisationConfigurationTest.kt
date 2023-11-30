package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.servlet.HandlerMapping
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
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

  @Test
  fun `authorised document types contains subject access request report when request has document type SAR role`() {
    req.addUserRole(ROLE_DOCUMENT_TYPE_SAR)

    interceptor.preHandle(req, res, "null")

    assertThat(req.authorisedDocumentTypes()).contains(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT)
  }

  @Test
  fun `document type subject access request report from document unique identifier throws exception when request does not have document type SAR role`() {
    val documentUuid = UUID.randomUUID()

    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentUuid" to documentUuid,
      ),
    )

    whenever(documentRepository.getDocumentTypeByDocumentUuid(documentUuid)).thenReturn(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT)

    assertThrows<AccessDeniedException>(
      "Document type '${DocumentType.SUBJECT_ACCESS_REQUEST_REPORT}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  @Test
  fun `document type subject access request report from path variable throws exception when request does not have document type SAR role`() {
    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentType" to DocumentType.SUBJECT_ACCESS_REQUEST_REPORT,
      ),
    )

    assertThrows<AccessDeniedException>(
      "Document type '${DocumentType.SUBJECT_ACCESS_REQUEST_REPORT}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  @Test
  fun `authorisation check prioritises document type from unique identifier over from path variable`() {
    val documentUuid = UUID.randomUUID()

    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentUuid" to documentUuid,
        "documentType" to DocumentType.HMCTS_WARRANT,
      ),
    )

    whenever(documentRepository.getDocumentTypeByDocumentUuid(documentUuid)).thenReturn(DocumentType.SUBJECT_ACCESS_REQUEST_REPORT)

    assertThrows<AccessDeniedException>(
      "Document type '${DocumentType.SUBJECT_ACCESS_REQUEST_REPORT}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  @Test
  fun `authorisation check uses document type from path variable if document type from unique identifier is null`() {
    val documentUuid = UUID.randomUUID()

    req.setAttribute(
      HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
      mapOf(
        "documentUuid" to documentUuid,
        "documentType" to DocumentType.SUBJECT_ACCESS_REQUEST_REPORT,
      ),
    )

    whenever(documentRepository.getDocumentTypeByDocumentUuid(documentUuid)).thenReturn(null)

    assertThrows<AccessDeniedException>(
      "Document type '${DocumentType.SUBJECT_ACCESS_REQUEST_REPORT}' requires additional role",
    ) {
      interceptor.preHandle(req, res, "null")
    }
  }

  private fun HttpServletRequest.authorisedDocumentTypes() =
    (getAttribute(AUTHORISED_DOCUMENT_TYPES) as List<*>).filterIsInstance<DocumentType>()
}
