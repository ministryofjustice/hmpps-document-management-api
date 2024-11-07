package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration

enum class S3BucketName(
  val value: String,
) {
  DOCUMENT_MANAGEMENT("document-management"),
  PRISONER_IMAGES("prisoner-images"),
  DISTINGUISHING_MARK_IMAGES("distinguishing-mark-images"),
}
