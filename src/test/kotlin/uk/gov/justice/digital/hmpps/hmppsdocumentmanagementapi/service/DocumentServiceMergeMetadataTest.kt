package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.AdditionalAnswers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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

class DocumentServiceMergeMetadataTest {
  private val documentRepository: DocumentRepository = mock()
  private val eventService: EventService = mock()
  private val documentDuplicateService: DocumentDuplicateService = mock()

  private val service = DocumentService(
    documentRepository,
    mock(),
    eventService,
    mock(),
    DocumentHashingProperties(),
    documentDuplicateService,
  )

  private val documentUuid = UUID.randomUUID()
  private val document = spy(
    Document(
      documentId = 1,
      documentUuid = documentUuid,
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant_for_sentencing",
      fileExtension = "pdf",
      fileSize = 3876,
      fileHash = "d58e3582afa99040e27b92b13c8f2280",
      mimeType = "application/pdf",
      metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\", \"status\": \"AWAITING\" }"),
      createdByServiceName = "Remand and sentencing",
      createdByUsername = "CREATED_BY_USERNAME",
    ),
  )

  private val mergeMetadata = ObjectMapper().readTree("{ \"status\": \"ACTIVE\" }")

  private val documentRequestContext = DocumentRequestContext(
    "Merged metadata using service name",
    "KMI",
    "MERGED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())
  }

  @Test
  fun `throws exception when document not found`() {
    assertThrows<EntityNotFoundException> {
      service.mergeDocumentMetadata(UUID.randomUUID(), mergeMetadata, documentRequestContext)
    }
  }

  @Test
  fun `saves and flushes document`() {
    service.mergeDocumentMetadata(documentUuid, mergeMetadata, documentRequestContext)

    verify(documentRepository).saveAndFlush(document)
  }

  @Test
  fun `re-canonicalises the content group after merging metadata`() {
    service.mergeDocumentMetadata(documentUuid, mergeMetadata, documentRequestContext)

    verify(documentDuplicateService).redetermineCanonicalFor(document)
  }
}
