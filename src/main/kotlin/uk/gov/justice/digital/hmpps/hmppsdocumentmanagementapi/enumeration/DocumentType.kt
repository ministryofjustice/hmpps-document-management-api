package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ROLE_DOCUMENT_TYPE_SAR

enum class DocumentType(
  val description: String,
  val additionalRoles: Collection<String> = emptySet(),
) {
  HMCTS_WARRANT(
    description = "Warrants for Remand and Sentencing",
  ),
  SUBJECT_ACCESS_REQUEST_REPORT(
    description = "Subject Access Request Report",
    additionalRoles = setOf(ROLE_DOCUMENT_TYPE_SAR),
  ),
  CVL_DOCS(
    description = "Offender Licence documents created and managed by CVL (create and vary a licence create-and-vary-a-licence-api)",
  ),
}
