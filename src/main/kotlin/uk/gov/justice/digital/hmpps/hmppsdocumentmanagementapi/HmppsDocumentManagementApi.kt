package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HmppsDocumentManagementApi

fun main(args: Array<String>) {
  runApplication<HmppsDocumentManagementApi>(*args)
}
