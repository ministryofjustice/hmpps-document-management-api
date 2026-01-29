package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.AdditionalAnswers
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentMetadataReplacedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceReplaceMetadataTest {
  private val documentRepository: DocumentRepository = mock()
  private val eventService: EventService = mock()
  private val virusScanService: VirusScanService = mock()

  private val service = DocumentService(documentRepository, mock(), eventService, virusScanService)

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
      metadata = ObjectMapper().readTree("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"),
      createdByServiceName = "Remand and sentencing",
      createdByUsername = "CREATED_BY_USERNAME",
    ),
  )

  private val replacementMetadata = ObjectMapper().readTree("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

  private val documentRequestContext = DocumentRequestContext(
    "Replaced metadata using service name",
    "KMI",
    "REPLACED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(documentRepository.saveAndFlush(any<Document>())).thenAnswer(AdditionalAnswers.returnsFirstArg<Document>())
  }

  @Test
  fun `throws exception when document not found`() {
    assertThrows<EntityNotFoundException>(
      "Document with UUID '$documentUuid' not found",
    ) {
      service.replaceDocumentMetadata(UUID.randomUUID(), replacementMetadata, documentRequestContext)
    }
  }

  @Test
  fun `calls document replace metadata function`() {
    service.replaceDocumentMetadata(documentUuid, replacementMetadata, documentRequestContext)

    verify(document).replaceMetadata(
      eq(replacementMetadata),
      any<LocalDateTime>(),
      eq(documentRequestContext.serviceName),
      eq(documentRequestContext.username),
    )
  }

  @Test
  fun `saves and flushes document`() {
    service.replaceDocumentMetadata(documentUuid, replacementMetadata, documentRequestContext)

    verify(documentRepository).saveAndFlush(document)
  }

  @Test
  fun `records event`() {
    val originalMetadata = document.metadata

    service.replaceDocumentMetadata(documentUuid, replacementMetadata, documentRequestContext)

    val supersededTime = document.documentMetadataHistory().single().supersededTime

    verify(eventService).recordDocumentMetadataReplacedEvent(
      eq(DocumentMetadataReplacedEvent(document.toModel(), originalMetadata)),
      eq(documentRequestContext),
      eq(supersededTime),
      any<Long>(),
    )
  }

  @Test
  fun `returns document model`() {
    val result = service.replaceDocumentMetadata(documentUuid, replacementMetadata, documentRequestContext)

    verify(document).toModel()

    assertThat(result).isInstanceOf(DocumentModel::class.java)
  }
}
