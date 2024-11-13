package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.S3BucketName
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.AbstractDocumentTypeIntegrationTest
import java.util.UUID

@SqlGroup(
  Sql("classpath:test_data/reset-database.sql"),
  Sql("classpath:test_data/distinguishing-mark-image-test.sql"),
)
class DistinguishingMarkImageIntTest : AbstractDocumentTypeIntegrationTest() {
  override val documentType = DocumentType.DISTINGUISHING_MARK_IMAGE
  override val documentTypeRole = ROLE_DOCUMENT_TYPE_DISTINGUISHING_MARK_IMAGE
  override val documentUuid: UUID = UUID.fromString("dcfa4919-4474-461d-a795-336fbd11438c")
  override val metadata: JsonNode = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")
  override val serviceName = "distinguishing-marks-api"
  override val activeCaseLoadId = "KIR"
  override val username = "TEST_USER"
  final override val testFileName = "distinguishing-mark-image.png"
  override val resourcePathOfTestDocument = "test_data/$testFileName"
  override val documentFileSize: Long = 10465L
  override val contentType = "image/png"
  override val bucketName = S3BucketName.DISTINGUISHING_MARK_IMAGES.value
  override val testFileHash = "25429b1d1ef462265048f59522d04f07a1f81ee07d0abc39424d337d2b24d988"
}
