package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration

object TestConstants {
  const val INVALID_UUID = "INVALID"
  const val INVALID_DOCUMENT_TYPE = "INVALID"
  const val INVALID_UUID_EXCEPTION_MESSAGE_TEMPLATE =
    "Method parameter 'documentUuid': Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Invalid UUID string: %s"
  const val INVALID_DOC_TYPE_EXCEPTION_MESSAGE_TEMPLATE =
    "Method parameter 'documentType': Failed to convert value of type 'java.lang.String' to required type 'uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType'; Failed to convert from type [java.lang.String] to type [@org.springframework.web.bind.annotation.PathVariable @io.swagger.v3.oas.annotations.Parameter uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType] for value [%s]"
}
