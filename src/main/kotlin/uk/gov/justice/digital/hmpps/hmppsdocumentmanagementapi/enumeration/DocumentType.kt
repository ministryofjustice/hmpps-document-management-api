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
  HMPPS_LICENCE_EXCLUSION_ZONE_MAP(
    description = "Map image for the exclusion zone condition added to the licence",
  ),
  HMPPS_LICENCE_EXCLUSION_ZONE_MAP_ORIG_DATA(
    description = "Original data for the exclusion zone condition added to the licence",
  ),
  HMPPS_LICENCE_EXCLUSION_ZONE_MAP_THUMBNAIL(
    description = "Original data for the exclusion zone condition added to the licence",
  ),
}
