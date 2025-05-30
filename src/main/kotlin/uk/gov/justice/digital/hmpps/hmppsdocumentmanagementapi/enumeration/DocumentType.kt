package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ROLE_DOCUMENT_TYPE_SAR

enum class DocumentType(
  val description: String,
  val s3BucketName: S3BucketName = S3BucketName.DOCUMENT_MANAGEMENT,
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
  PPUD_RECALL(
    description = "Supporting documents for PPUD",
  ),
  PRISONER_PROFILE_PICTURE(
    description = "Photograph of prisoner used for profile",
    s3BucketName = S3BucketName.PRISONER_IMAGES,
  ),
  CEMO_ATTACHMENT(
    description = "Attachments used for create electronic monitoring order",
  ),
  TRIAL_RECORD_SHEET(
    description = "Trial record sheet of a Sentence",
  ),
  INDICTMENT(
    description = "Indictment document of a Sentence",
  ),
  PRISON_COURT_REGISTER(
    description = "Prison court register",
  ),
}
