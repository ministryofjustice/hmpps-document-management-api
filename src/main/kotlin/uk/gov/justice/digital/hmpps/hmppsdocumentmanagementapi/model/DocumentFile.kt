package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import java.io.InputStream

data class DocumentFile(
  val filename: String,
  val fileSize: Long,
  val mimeType: String,
  val inputStream: InputStream,
)
