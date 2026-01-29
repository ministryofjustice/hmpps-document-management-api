package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource


import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.S3BucketName
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.AbstractDocumentTypeIntegrationTest
import java.util.UUID

@SqlGroup(
  Sql("classpath:test_data/reset-database.sql"),
  Sql("classpath:test_data/subject-access-request-report-id-4.sql"),
)
class SubjectAccessRequestIntTest : AbstractDocumentTypeIntegrationTest() {
  override val documentType = DocumentType.SUBJECT_ACCESS_REQUEST_REPORT
  override val documentTypeRole = ROLE_DOCUMENT_TYPE_SAR
  override val documentUuid: UUID = UUID.fromString("1f4e2c96-de62-4585-a79a-9a37c5506b1c")
  override val metadata: JsonNode =
    ObjectMapper().readTree("{ \"sarCaseReference\": \"SAR-1234\", \"prisonNumber\": \"A1234BC\" }")
  override val serviceName = "Manage Subject Access Requests"
  override val activeCaseLoadId = "STI"
  override val username = "SAR_USER"
  final override val testFileName = "subject-access-request-report.pdf"
  override val resourcePathOfTestDocument = "test_data/$testFileName"
  override val documentFileSize: Long = 21384L
  override val contentType = "application/pdf"
  override val bucketName = S3BucketName.DOCUMENT_MANAGEMENT.value
  override val testFileHash = "d58e3582afa99040e27b92b13c8f2280"
}
