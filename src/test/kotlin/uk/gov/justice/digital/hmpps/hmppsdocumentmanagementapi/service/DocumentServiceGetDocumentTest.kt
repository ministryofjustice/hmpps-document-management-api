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
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceGetDocumentTest {
  private val documentRepository: DocumentRepository = mock()

  private val service = DocumentService(documentRepository, mock())

  private val documentUuid = UUID.randomUUID()
  private val document = mock<Document>()
  private val documentModel = mock<DocumentModel>()

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
    whenever(document.toModel()).thenReturn(documentModel)
  }

  @Test
  fun `throws exception when document not found`() {
    UUID.randomUUID().apply {
      assertThrows<EntityNotFoundException>("Document with UUID '$this' not found.") {
        service.getDocument(this)
      }
    }
  }

  @Test
  fun `finds document by unique identifier`() {
    service.getDocument(documentUuid)

    verify(documentRepository).findByDocumentUuid(documentUuid)
  }

  @Test
  fun `returns document model`() {
    assertThat(service.getDocument(documentUuid)).isEqualTo(documentModel)

    verify(document).toModel()
  }
}
