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
  EXCLUSION_ZONE_MAP(
    description = "Exclusion zone maps used for exclusion zone condition on offender licence",
  ),
  PIC_CASE_DOCUMENTS(
    description = "Documents uploaded by prepare-a-case application users.",
  ),
}
