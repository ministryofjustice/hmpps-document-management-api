package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.time.LocalDate

@ConfigurationProperties(prefix = "document.purge")
data class DocumentPurgeProperties(
  val batchSize: Int = 250,
  val before: LocalDate = LocalDate.parse("1970-01-01"),
  val documentTypes: Set<DocumentType> = emptySet(),
)
