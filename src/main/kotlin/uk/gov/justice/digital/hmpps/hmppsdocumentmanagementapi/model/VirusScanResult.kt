package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.VirusScanStatus

data class VirusScanResult(
  val status: VirusScanStatus,
  val result: String?,
  val signature: String? = null,
)
