package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.AdditionalAnswers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentHashingProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID

class DocumentServiceSetFileContentHashTest {
  private val documentRepository: DocumentRepository = mock()

  private val service = DocumentService(
    documentRepository,
    mock(),
    mock(),
    mock(),
    DocumentHashingProperties(contentHashDocumentTypes = setOf(DocumentType.HMCTS_WARRANT)),
  )

  private val documentUuid = UUID.randomUUID()
  private val contentHash = "58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae"

  private val documentRequestContext = DocumentRequestContext(
    "court-data-ingestion-api",
    "KMI",
    "SYSTEM",
  )

  private fun warrant(existingContentHash: String? = null) = spy(
    Document(
      documentId = 1,
      documentUuid = documentUuid,
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant_for_sentencing",
      fileExtension = "pdf",
      fileSize = 3876,
      fileHash = "d58e3582afa99040e27b92b13c8f2280",
      mimeType = "application/pdf",
      metadata = ObjectMapper().readTree("{}"),
      createdByServiceName = "Remand and sentencing",
      createdByUsername = "CREATED_BY_USERNAME",
      fileContentHash = existingContentHash,
    ),
  )

  @Test
  fun `sets the content hash when previously absent`() {
    val document = warrant(existingContentHash = null)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())

    val result = service.setFileContentHash(documentUuid, contentHash, documentRequestContext)

    assertThat(document.fileContentHash).isEqualTo(contentHash)
    assertThat(result.fileContentHash).isEqualTo(contentHash)
    verify(documentRepository).saveAndFlush(document)
  }

  @Test
  fun `lowercases the supplied hash`() {
    val document = warrant(existingContentHash = null)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())

    service.setFileContentHash(documentUuid, contentHash.uppercase(), documentRequestContext)

    assertThat(document.fileContentHash).isEqualTo(contentHash)
  }

  @Test
  fun `does not save when the value is unchanged`() {
    val document = warrant(existingContentHash = contentHash)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)

    service.setFileContentHash(documentUuid, contentHash, documentRequestContext)

    verify(documentRepository, never()).saveAndFlush(any<Document>())
  }

  @Test
  fun `rejects a document type that does not support a content hash`() {
    val sar = spy(
      Document(
        documentId = 2,
        documentUuid = documentUuid,
        documentType = DocumentType.SUBJECT_ACCESS_REQUEST_REPORT,
        filename = "subject_access_request_report",
        fileExtension = "pdf",
        fileSize = 10,
        fileHash = "0e0396b7a0e931762c167abb7da85398",
        mimeType = "application/pdf",
        metadata = ObjectMapper().readTree("{}"),
        createdByServiceName = "Manage Subject Access Requests",
        createdByUsername = "SAR_USER",
      ),
    )
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(sar)

    assertThrows<IllegalArgumentException> {
      service.setFileContentHash(documentUuid, contentHash, documentRequestContext)
    }
    verify(documentRepository, never()).saveAndFlush(any<Document>())
  }

  @Test
  fun `throws when the document is not found`() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(warrant())

    assertThrows<EntityNotFoundException> {
      service.setFileContentHash(UUID.randomUUID(), contentHash, documentRequestContext)
    }
  }
}
