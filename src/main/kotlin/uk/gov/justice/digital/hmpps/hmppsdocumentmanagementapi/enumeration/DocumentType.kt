package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration

enum class DocumentType(
  val description: String,
) {
  HMCTS_WARRANT("Warrants for Remand and Sentencing"),
  SUBJECT_ACCESS_REQUEST_REPORT("Subject Access Request Report"),
}
