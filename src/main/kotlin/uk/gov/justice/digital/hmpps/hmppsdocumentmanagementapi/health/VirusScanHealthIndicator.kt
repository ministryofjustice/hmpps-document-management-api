package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.health

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.VirusScanService

@Component
@ConditionalOnEnabledHealthIndicator("virus.scan")
class VirusScanHealthIndicator(private val virusScanService: VirusScanService) : HealthIndicator {
  override fun health(): Health {
    val result = virusScanService.ping()
    val health = if (result) {
      Health.up()
    } else {
      Health.down()
    }
    return health.build()
  }
}
