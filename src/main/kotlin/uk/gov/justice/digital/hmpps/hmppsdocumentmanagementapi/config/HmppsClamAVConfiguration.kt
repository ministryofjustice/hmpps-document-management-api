package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(HmppsClamAVProperties::class)
class HmppsClamAVConfiguration
