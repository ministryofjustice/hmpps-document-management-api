package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.io.InputStream
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceGetDocumentFileTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentFileService: DocumentFileService = mock()

  private val service = DocumentService(documentRepository, documentFileService)

  private val documentUuid = UUID.randomUUID()
  private val documentFilename = "test.pdf"
  private val fileSize = 1234L
  private val mimeType = "application/pdf"
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()
  private val documentFile = mock<InputStream>()

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(document.toModel()).thenReturn(documentModel)
    whenever(documentModel.documentFilename).thenReturn(documentFilename)
    whenever(documentModel.fileSize).thenReturn(fileSize)
    whenever(documentModel.mimeType).thenReturn(mimeType)
    whenever(documentFileService.getDocumentFile(documentUuid)).thenReturn(documentFile)
  }

  @Test
  fun `throws exception when document not found`() {
    UUID.randomUUID().apply {
      assertThrows<EntityNotFoundException>("Document with UUID '$this' not found.") {
        service.getDocumentFile(this)
      }
    }
  }

  @Test
  fun `finds document by unique identifier`() {
    service.getDocumentFile(documentUuid)

    verify(documentRepository).findByDocumentUuid(documentUuid)
  }

  @Test
  fun `gets document file by unique identifier`() {
    service.getDocumentFile(documentUuid)

    verify(documentFileService).getDocumentFile(documentUuid)
  }

  @Test
  fun `populates document file model from document model`() {
    with(service.getDocumentFile(documentUuid)) {
      assertThat(filename).isEqualTo(documentFilename)
      assertThat(fileSize).isEqualTo(this@DocumentServiceGetDocumentFileTest.fileSize)
      assertThat(mimeType).isEqualTo(this@DocumentServiceGetDocumentFileTest.mimeType)
    }

    verify(document).toModel()
  }

  @Test
  fun `assigns document file input stream to model`() {
    assertThat(service.getDocumentFile(documentUuid).inputStream).isEqualTo(documentFile)
  }
}
