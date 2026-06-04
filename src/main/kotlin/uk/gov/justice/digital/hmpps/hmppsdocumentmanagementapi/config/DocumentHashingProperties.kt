package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

@ConfigurationProperties("document.hashing")
data class DocumentHashingProperties(
  val fileHashDocumentTypes: Set<DocumentType> = emptySet(),
  val contentHashDocumentTypes: Set<DocumentType> = emptySet(),
)
