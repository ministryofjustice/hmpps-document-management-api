package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentAlreadyUploadedException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentHashingProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceUploadDocumentTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentFileService: DocumentFileService = mock()
  private val eventService: EventService = mock()
  private val virusScanService: VirusScanService = mock()

  private val hashingProperties = DocumentHashingProperties(
    fileHashDocumentTypes = setOf(DocumentType.HMCTS_WARRANT),
    contentHashDocumentTypes = setOf(DocumentType.HMCTS_WARRANT),
  )
  private val service = DocumentService(documentRepository, documentFileService, eventService, virusScanService, hashingProperties)
  private val serviceWithHashingDisabled =
    DocumentService(documentRepository, documentFileService, eventService, virusScanService, DocumentHashingProperties())

  private val documentType = DocumentType.HMCTS_WARRANT
  private val documentUuid = UUID.randomUUID()
  private val file = mock<MultipartFile>()
  private val size = 1234L
  private val contentType = "application/pdf"
  private val documentRequestContext = DocumentRequestContext(
    "Uploaded using service name",
    "PVI",
    "UPLOADED_BY_USERNAME",
  )
  private val documentCaptor = argumentCaptor<Document>()
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()

  @BeforeEach
  fun setUp() {
    whenever(file.originalFilename).thenReturn("test.pdf")
    whenever(file.size).thenReturn(size)
    whenever(file.contentType).thenReturn(contentType)
    whenever(file.inputStream).thenReturn("A".byteInputStream())
    whenever(documentRepository.saveAndFlush(documentCaptor.capture())).thenReturn(document)
    whenever(document.toModel()).thenReturn(documentModel)
  }

  @Test
  fun `throws exception when document found`() {
    whenever(documentRepository.findByDocumentUuidIncludingSoftDeleted(documentUuid)).thenReturn(mock<Document>())
    assertThrows<DocumentAlreadyUploadedException>("Document with UUID '$documentUuid' already uploaded.") {
      service.uploadDocument(mock(), documentUuid, mock(), mock(), mock())
    }
  }

  @Test
  fun `assigns supplied document unique identifier, type and metadata to entity`() {
    val metadata = mock<JsonNode>()

    service.uploadDocument(documentType, documentUuid, file, metadata, documentRequestContext)

    with(documentCaptor.firstValue) {
      assertThat(documentUuid).isEqualTo(this@DocumentServiceUploadDocumentTest.documentUuid)
      assertThat(documentType).isEqualTo(this@DocumentServiceUploadDocumentTest.documentType)
      assertThat(this.metadata).isEqualTo(metadata)
    }
  }

  @Test
  fun `assigns multipart file properties to entity`() {
    service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    with(documentCaptor.firstValue) {
      assertThat(filename).isEqualTo("test")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(size)
      assertThat(mimeType).isEqualTo(contentType)
    }
  }

  @Test
  fun `computes the sha-256 file hash from the uploaded file when none is supplied`() {
    service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    // sha-256 of the stubbed file content "A"
    assertThat(documentCaptor.firstValue.fileHash)
      .isEqualTo("559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd")
  }

  @Test
  fun `uses the supplied file hash verbatim and does not recompute`() {
    service.uploadDocument(
      documentType,
      documentUuid,
      file,
      mock(),
      documentRequestContext,
      fileHash = "supplied-file-hash",
    )

    assertThat(documentCaptor.firstValue.fileHash).isEqualTo("supplied-file-hash")
  }

  @Test
  fun `falls back to computing the file hash when the supplied value is blank`() {
    service.uploadDocument(
      documentType,
      documentUuid,
      file,
      mock(),
      documentRequestContext,
      fileHash = "  ",
    )

    assertThat(documentCaptor.firstValue.fileHash)
      .isEqualTo("559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd")
  }

  @Test
  fun `stores the supplied content hash and leaves it null when not supplied`() {
    service.uploadDocument(
      documentType,
      documentUuid,
      file,
      mock(),
      documentRequestContext,
      fileContentHash = "supplied-content-hash",
    )
    assertThat(documentCaptor.firstValue.fileContentHash).isEqualTo("supplied-content-hash")

    service.uploadDocument(documentType, UUID.randomUUID(), file, mock(), documentRequestContext)
    assertThat(documentCaptor.lastValue.fileContentHash).isNull()
  }

  @Test
  fun `leaves the file hash empty for a document type that is not enabled for hashing`() {
    serviceWithHashingDisabled.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    assertThat(documentCaptor.firstValue.fileHash).isEmpty()
  }

  @Test
  fun `uses a supplied file hash even when the document type is not enabled for hashing`() {
    serviceWithHashingDisabled.uploadDocument(
      documentType,
      documentUuid,
      file,
      mock(),
      documentRequestContext,
      fileHash = "supplied-file-hash",
    )

    assertThat(documentCaptor.firstValue.fileHash).isEqualTo("supplied-file-hash")
  }

  @Test
  fun `ignores a supplied content hash for a document type that is not enabled`() {
    serviceWithHashingDisabled.uploadDocument(
      documentType,
      documentUuid,
      file,
      mock(),
      documentRequestContext,
      fileContentHash = "supplied-content-hash",
    )

    assertThat(documentCaptor.firstValue.fileContentHash).isNull()
  }

  @Test
  fun `uses now as the created time value`() {
    service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    assertThat(documentCaptor.firstValue.createdTime).isCloseTo(LocalDateTime.now(), Assertions.within(3, ChronoUnit.SECONDS))
  }

  @Test
  fun `assigns supplied document context properties to entity`() {
    service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    with(documentCaptor.firstValue) {
      assertThat(createdByServiceName).isEqualTo(documentRequestContext.serviceName)
      assertThat(createdByUsername).isEqualTo(documentRequestContext.username)
    }
  }

  @Test
  fun `saves document file using unique identifier`() {
    service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    verify(documentFileService).saveDocumentFile(documentUuid, file, documentType)
  }

  @Test
  fun `records event`() {
    service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)

    verify(eventService).recordDocumentUploadedEvent(eq(documentModel), eq(documentRequestContext), any<Long>())
  }

  @Test
  fun `returns document model`() {
    assertThat(service.uploadDocument(documentType, documentUuid, file, mock(), documentRequestContext)).isEqualTo(documentModel)

    verify(document).toModel()
  }
}
