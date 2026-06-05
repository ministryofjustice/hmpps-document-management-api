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

class DocumentServiceSetDuplicateOfTest {
  private val documentRepository: DocumentRepository = mock()

  private val service = DocumentService(
    documentRepository,
    mock(),
    mock(),
    mock(),
    DocumentHashingProperties(),
  )

  private val documentUuid = UUID.randomUUID()
  private val canonicalUuid = UUID.randomUUID()

  private val documentRequestContext = DocumentRequestContext(
    "court-data-ingestion-api",
    "KMI",
    "SYSTEM",
  )

  private fun warrant(existingDuplicateOf: UUID? = null) = spy(
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
    ),
  ).also { it.duplicateOf = existingDuplicateOf }

  @Test
  fun `sets the duplicate relationship when previously absent`() {
    val document = warrant(existingDuplicateOf = null)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())

    val result = service.setDuplicateOf(documentUuid, canonicalUuid, documentRequestContext)

    assertThat(document.duplicateOf).isEqualTo(canonicalUuid)
    assertThat(result.duplicateOf).isEqualTo(canonicalUuid)
    verify(documentRepository).saveAndFlush(document)
  }

  @Test
  fun `clears the duplicate relationship when set to null`() {
    val document = warrant(existingDuplicateOf = canonicalUuid)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())

    val result = service.setDuplicateOf(documentUuid, null, documentRequestContext)

    assertThat(document.duplicateOf).isNull()
    assertThat(result.duplicateOf).isNull()
    verify(documentRepository).saveAndFlush(document)
  }

  @Test
  fun `does not save when the value is unchanged`() {
    val document = warrant(existingDuplicateOf = canonicalUuid)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)

    service.setDuplicateOf(documentUuid, canonicalUuid, documentRequestContext)

    verify(documentRepository, never()).saveAndFlush(any<Document>())
  }

  @Test
  fun `rejects a document that is a duplicate of itself`() {
    val document = warrant(existingDuplicateOf = null)
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)

    assertThrows<IllegalArgumentException> {
      service.setDuplicateOf(documentUuid, documentUuid, documentRequestContext)
    }
    verify(documentRepository, never()).saveAndFlush(any<Document>())
  }

  @Test
  fun `throws when the document is not found`() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(warrant())

    assertThrows<EntityNotFoundException> {
      service.setDuplicateOf(UUID.randomUUID(), canonicalUuid, documentRequestContext)
    }
  }
}
