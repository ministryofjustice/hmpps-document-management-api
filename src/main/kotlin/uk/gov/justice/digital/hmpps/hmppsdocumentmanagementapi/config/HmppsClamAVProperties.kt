package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("hmpps.clamav")
data class HmppsClamAVProperties(
  val chunkSize: Int = 2048,
  val connectionTimeout: Int = 5000,
  val readTimeout: Int = 5000,
  val host: String = "localhost",
  val port: Int = 3310,
)
