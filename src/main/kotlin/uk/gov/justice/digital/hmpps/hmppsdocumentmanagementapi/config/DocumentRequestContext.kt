package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

data class DocumentRequestContext(
  val serviceName: String,
  val activeCaseLoadId: String?,
  val username: String?,
)
