package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsS3Properties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class DocumentFileHashBackfillServiceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var documentRepository: DocumentRepository

  @Autowired
  private lateinit var fileHashBackfillService: DocumentFileHashBackfillService

  @Autowired
  private lateinit var hmppsS3Properties: HmppsS3Properties

  private val courtIngestion = "court-data-ingestion-api"

  private fun bucketFor(documentType: DocumentType) = hmppsS3Properties.buckets[documentType.s3BucketName.value]!!.bucketName

  private fun putObject(documentUuid: UUID, bytes: ByteArray, documentType: DocumentType = DocumentType.HMCTS_WARRANT) {
    s3Client.putObject(
      PutObjectRequest.builder().bucket(bucketFor(documentType)).key(documentUuid.toString()).build(),
      RequestBody.fromBytes(bytes),
    )
  }

  private fun seedBlankHash(ageMinutes: Long, serviceName: String = courtIngestion): Document = documentRepository.saveAndFlush(
    Document(
      documentUuid = UUID.randomUUID(),
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant",
      fileExtension = "pdf",
      fileSize = 1234,
      fileHash = "",
      mimeType = "application/pdf",
      metadata = objectMapper.readTree("{}"),
      createdTime = LocalDateTime.now().minusMinutes(ageMinutes),
      createdByServiceName = serviceName,
      createdByUsername = "USER",
      fileContentHash = null,
    ),
  )

  private fun reload(document: Document) = documentRepository.findByDocumentUuid(document.documentUuid)!!

  @Test
  fun `populates a missing byte hash and links byte-identical copies`() {
    val bytes = "identical-warrant-bytes".toByteArray()
    val older = seedBlankHash(ageMinutes = 10)
    val newer = seedBlankHash(ageMinutes = 5)
    putObject(older.documentUuid, bytes)
    putObject(newer.documentUuid, bytes)

    fileHashBackfillService.run()

    val reloadedOlder = reload(older)
    val reloadedNewer = reload(newer)
    assertThat(reloadedOlder.fileHash).isNotBlank()
    assertThat(reloadedNewer.fileHash).isEqualTo(reloadedOlder.fileHash)
    assertThat(reloadedOlder.duplicateOf).isNull()
    assertThat(reloadedNewer.duplicateOf).isEqualTo(older.documentUuid)
  }

  @Test
  fun `distinct bytes are hashed but not linked`() {
    val a = seedBlankHash(ageMinutes = 10)
    val b = seedBlankHash(ageMinutes = 5)
    putObject(a.documentUuid, "warrant-a".toByteArray())
    putObject(b.documentUuid, "warrant-b".toByteArray())

    fileHashBackfillService.run()

    assertThat(reload(a).fileHash).isNotBlank()
    assertThat(reload(b).fileHash).isNotBlank()
    assertThat(reload(a).fileHash).isNotEqualTo(reload(b).fileHash)
    assertThat(reload(a).duplicateOf).isNull()
    assertThat(reload(b).duplicateOf).isNull()
  }

  @Test
  fun `a missing S3 object is skipped and does not abort the run`() {
    val present = seedBlankHash(ageMinutes = 10)
    val missing = seedBlankHash(ageMinutes = 5)
    putObject(present.documentUuid, "present".toByteArray())
    // deliberately store no object for the missing document

    fileHashBackfillService.run()

    assertThat(reload(present).fileHash).isNotBlank()
    assertThat(reload(missing).fileHash).isEmpty()
  }

  @Test
  fun `re-running is a no-op once hashes are populated`() {
    val doc = seedBlankHash(ageMinutes = 10)
    putObject(doc.documentUuid, "warrant".toByteArray())
    fileHashBackfillService.run()
    val firstHash = reload(doc).fileHash
    assertThat(firstHash).isNotBlank()

    fileHashBackfillService.run()

    assertThat(reload(doc).fileHash).isEqualTo(firstHash)
  }
}
