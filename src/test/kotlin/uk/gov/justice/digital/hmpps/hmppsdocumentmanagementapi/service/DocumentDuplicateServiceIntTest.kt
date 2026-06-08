package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class DocumentDuplicateServiceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var documentRepository: DocumentRepository

  @Autowired
  private lateinit var documentDuplicateService: DocumentDuplicateService

  @Autowired
  private lateinit var documentService: DocumentService

  private val courtIngestion = "court-data-ingestion-api"
  private val remandAndSentencing = "Remand and Sentencing"

  private var sequence = 0L

  private fun save(
    serviceName: String,
    ageMinutes: Long,
    fileContentHash: String? = null,
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
      metadata = objectMapper.readTree("{}"),
      createdTime = LocalDateTime.now().minusMinutes(ageMinutes),
      createdByServiceName = serviceName,
      createdByUsername = "USER",
      fileContentHash = fileContentHash,
    ),
  )

  private fun reload(document: Document) = documentRepository.findByDocumentUuid(document.documentUuid)

  @Test
  fun `among content-equal documents the older is canonical`() {
    val older = save(courtIngestion, ageMinutes = 10, fileContentHash = "content-1", fileHash = "byte-a")
    val newer = save(courtIngestion, ageMinutes = 5, fileContentHash = "content-1", fileHash = "byte-b")

    documentDuplicateService.redetermineCanonicalFor(newer)

    assertThat(reload(older)!!.duplicateOf).isNull()
    assertThat(reload(newer)!!.duplicateOf).isEqualTo(older.documentUuid)
  }

  @Test
  fun `an authoritative service wins regardless of age and links by byte hash`() {
    val courtCopy = save(courtIngestion, ageMinutes = 10, fileContentHash = "content-2", fileHash = "byte-shared")
    val rasCopy = save(remandAndSentencing, ageMinutes = 1, fileContentHash = null, fileHash = "byte-shared")

    documentDuplicateService.redetermineCanonicalFor(rasCopy)

    assertThat(reload(rasCopy)!!.duplicateOf).isNull()
    assertThat(reload(courtCopy)!!.duplicateOf).isEqualTo(rasCopy.documentUuid)
  }

  @Test
  fun `transitive set links content and byte matches and the authoritative copy is canonical`() {
    val a = save(courtIngestion, ageMinutes = 10, fileContentHash = "content-3", fileHash = "byte-pecs")
    val b = save(courtIngestion, ageMinutes = 9, fileContentHash = "content-3", fileHash = "byte-prison")
    val ras = save(remandAndSentencing, ageMinutes = 1, fileContentHash = null, fileHash = "byte-pecs")

    documentDuplicateService.redetermineCanonicalFor(a)

    assertThat(reload(ras)!!.duplicateOf).isNull()
    assertThat(reload(a)!!.duplicateOf).isEqualTo(ras.documentUuid)
    assertThat(reload(b)!!.duplicateOf).isEqualTo(ras.documentUuid)
  }

  @Test
  fun `deleting the canonical promotes the next`() {
    val older = save(courtIngestion, ageMinutes = 10, fileContentHash = "content-4", fileHash = "byte-c")
    val newer = save(courtIngestion, ageMinutes = 5, fileContentHash = "content-4", fileHash = "byte-d")
    documentDuplicateService.redetermineCanonicalFor(newer)
    assertThat(reload(newer)!!.duplicateOf).isEqualTo(older.documentUuid)

    documentService.deleteDocument(older.documentUuid, DocumentRequestContext(courtIngestion, "KMI", "USER"))

    assertThat(reload(older)).isNull()
    assertThat(reload(newer)!!.duplicateOf).isNull()
  }

  @Test
  fun `documents with neither hash in common are not grouped`() {
    val a = save(courtIngestion, ageMinutes = 5, fileContentHash = "content-5", fileHash = "byte-e")
    val b = save(courtIngestion, ageMinutes = 5, fileContentHash = "content-6", fileHash = "byte-f")

    documentDuplicateService.redetermineCanonicalFor(a)

    assertThat(reload(a)!!.duplicateOf).isNull()
    assertThat(reload(b)!!.duplicateOf).isNull()
  }

  @Test
  fun `blank byte hashes are never grouped together`() {
    val a = save(courtIngestion, ageMinutes = 5, fileContentHash = "content-7", fileHash = "")
    val b = save(courtIngestion, ageMinutes = 5, fileContentHash = "content-8", fileHash = "")

    documentDuplicateService.redetermineCanonicalFor(a)

    assertThat(reload(a)!!.duplicateOf).isNull()
    assertThat(reload(b)!!.duplicateOf).isNull()
  }
}
