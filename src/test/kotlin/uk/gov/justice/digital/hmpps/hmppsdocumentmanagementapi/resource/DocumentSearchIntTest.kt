package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.junit.jupiter.api.Test
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase

class DocumentSearchIntTest : IntegrationTestBase() {
  @Sql("classpath:test_data/document-search.sql")
  @Test
  fun `retrieve document with no metadata history from database`() {
  }
}
