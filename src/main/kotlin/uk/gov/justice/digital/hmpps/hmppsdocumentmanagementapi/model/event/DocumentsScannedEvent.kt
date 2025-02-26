package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext

data class DocumentsScannedEvent(
  val documentRequestContext: DocumentRequestContext,
  val fileSize: Long,
)
