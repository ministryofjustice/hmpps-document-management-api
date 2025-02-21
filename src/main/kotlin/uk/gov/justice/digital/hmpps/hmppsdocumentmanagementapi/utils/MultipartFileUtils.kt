package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils

import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

fun MultipartFile.filename(documentType: DocumentType) = originalFilename?.trim().let {
  if (it.isNullOrEmpty()) {
    documentType.toString()
  } else {
    it.withoutPath()
  }
}

fun MultipartFile.filenameWithoutExtension(documentType: DocumentType) = filename(documentType).let {
  if (it.contains('.')) {
    it.substring(0, it.lastIndexOf('.'))
  } else {
    it
  }
}

fun MultipartFile.fileExtension(documentType: DocumentType) = filename(documentType).let {
  if (it.contains('.')) {
    it.substring(it.lastIndexOf('.') + 1)
  } else {
    ""
  }
}

fun MultipartFile.mimeType() = contentType?.trim().takeUnless { it.isNullOrEmpty() } ?: "application/octet-stream"
