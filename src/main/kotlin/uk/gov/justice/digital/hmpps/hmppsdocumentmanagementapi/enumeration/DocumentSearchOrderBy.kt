package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration

enum class DocumentSearchOrderBy(
  val property: String,
  val columnName: String = property,
) {
  FILENAME("filename"),
  FILE_EXTENSION("fileExtension", "file_extension"),
  FILESIZE("fileSize", "file_size"),
  CREATED_TIME("createdTime", "created_time"),
}
