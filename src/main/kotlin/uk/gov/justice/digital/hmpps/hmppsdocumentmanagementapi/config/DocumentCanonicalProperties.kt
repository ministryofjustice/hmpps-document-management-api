package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Priority used when the store designates the canonical document among a set of content-equivalent
 * documents. Service names are matched case insensitively against created_by_service_name; earlier in
 * the list wins. Anything not listed ranks below all listed services and is then ordered oldest first.
 * Deliberately not hard coded to any one service so this stays a general capability rather than a
 * single consumer's policy.
 */
@ConfigurationProperties("document.canonical")
data class DocumentCanonicalProperties(
  val authoritativeServiceNames: List<String> = emptyList(),
  val activeStatuses: Set<String> = emptySet(),
)
