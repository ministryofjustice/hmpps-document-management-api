package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

@TestPropertySource(properties = ["document.canonical.active-statuses=ACTIVE"])
class DocumentDuplicateServiceStatusAwareIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var documentRepository: DocumentRepository

  @Autowired
  private lateinit var documentDuplicateService: DocumentDuplicateService

  @Autowired
  private lateinit var documentService: DocumentService

  private val courtIngestion = "court-data-ingestion-api"

  private var sequence = 0L

  private fun save(
    ageMinutes: Long,
    status: String,
    fileContentHash: String,
    fileHash: String = "byte-${sequence++}",
  ): Document = documentRepository.saveAndFlush(
    Document(
      documentUuid = UUID.randomUUID(),
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant",
      fileExtension = "pdf",
      fileSize = 1234,
      fileHash = fileHash,
      mimeType = "application/pdf",
      metadata = objectMapper.readTree("{ \"status\": \"$status\" }"),
      createdTime = LocalDateTime.now().minusMinutes(ageMinutes),
      createdByServiceName = courtIngestion,
      createdByUsername = "USER",
      fileContentHash = fileContentHash,
    ),
  )

  private fun reload(document: Document) = documentRepository.findByDocumentUuid(document.documentUuid)!!

  private fun context() = DocumentRequestContext(courtIngestion, "KMI", "USER")

  @Test
  fun `a non-active document is ignored, so the active document stays canonical even though it is newer`() {
    val awaiting = save(ageMinutes = 10, status = "AWAITING", fileContentHash = "content-1")
    val active = save(ageMinutes = 5, status = "ACTIVE", fileContentHash = "content-1")

    documentDuplicateService.redetermineCanonicalFor(active)

    assertThat(reload(active).duplicateOf).isNull()
    assertThat(reload(awaiting).duplicateOf).isNull()
  }

  @Test
  fun `duplicates collapse only among active documents`() {
    val activeOlder = save(ageMinutes = 10, status = "ACTIVE", fileContentHash = "content-2")
    val activeNewer = save(ageMinutes = 5, status = "ACTIVE", fileContentHash = "content-2")
    val awaiting = save(ageMinutes = 1, status = "AWAITING", fileContentHash = "content-2")

    documentDuplicateService.redetermineCanonicalFor(activeNewer)

    assertThat(reload(activeOlder).duplicateOf).isNull()
    assertThat(reload(activeNewer).duplicateOf).isEqualTo(activeOlder.documentUuid)
    assertThat(reload(awaiting).duplicateOf).isNull()
  }

  @Test
  fun `promoting a document to active re-ranks the group and it can become the canonical`() {
    val active = save(ageMinutes = 5, status = "ACTIVE", fileContentHash = "content-3")
    val awaiting = save(ageMinutes = 10, status = "AWAITING", fileContentHash = "content-3")

    documentDuplicateService.redetermineCanonicalFor(active)
    assertThat(reload(active).duplicateOf).isNull()

    documentService.mergeDocumentMetadata(
      awaiting.documentUuid,
      objectMapper.readTree("{ \"status\": \"ACTIVE\" }"),
      context(),
    )

    assertThat(reload(awaiting).duplicateOf).isNull()
    assertThat(reload(active).duplicateOf).isEqualTo(awaiting.documentUuid)
  }

  @Test
  fun `promoting a document to active via replace also re-ranks the group`() {
    val active = save(ageMinutes = 5, status = "ACTIVE", fileContentHash = "content-3b")
    val awaiting = save(ageMinutes = 10, status = "AWAITING", fileContentHash = "content-3b")

    documentDuplicateService.redetermineCanonicalFor(active)
    assertThat(reload(active).duplicateOf).isNull()

    documentService.replaceDocumentMetadata(
      awaiting.documentUuid,
      objectMapper.readTree("{ \"status\": \"ACTIVE\" }"),
      context(),
    )

    assertThat(reload(awaiting).duplicateOf).isNull()
    assertThat(reload(active).duplicateOf).isEqualTo(awaiting.documentUuid)
  }

  @Test
  fun `when the shown copy is deleted, its still-active duplicate takes over so the document does not disappear`() {
    val shownCopy = save(ageMinutes = 10, status = "ACTIVE", fileContentHash = "content-4")
    val otherCopy = save(ageMinutes = 5, status = "ACTIVE", fileContentHash = "content-4")

    documentDuplicateService.redetermineCanonicalFor(otherCopy)
    assertThat(reload(shownCopy).duplicateOf).isNull()
    assertThat(reload(otherCopy).duplicateOf).isEqualTo(shownCopy.documentUuid)

    documentService.mergeDocumentMetadata(
      shownCopy.documentUuid,
      objectMapper.readTree("{ \"status\": \"DELETED\" }"),
      context(),
    )

    // The surviving active copy is now the standalone canonical, still visible.
    assertThat(reload(otherCopy).duplicateOf).isNull()
  }
}
