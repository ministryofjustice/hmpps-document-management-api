package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class DocumentServiceDeleteDocumentTest {
  private val documentRepository: DocumentRepository = mock()

  private val service = DocumentService(documentRepository, mock())

  private val documentUuid = UUID.randomUUID()
  private val document = mock<Document>()

  private val documentRequestContext = DocumentRequestContext(
    "Deleted using service name",
    "DELETED_BY_USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(documentRepository.findByDocumentUuid(documentUuid)).thenReturn(document)
  }

  @Test
  fun `does not throw exception when document not found`() {
    service.deleteDocument(UUID.randomUUID(), documentRequestContext)

    verify(documentRepository, never()).saveAndFlush(any<Document>())
  }

  @Test
  fun `calls document replace metadata function`() {
    service.deleteDocument(documentUuid, documentRequestContext)

    verify(document).delete(
      any<LocalDateTime>(),
      eq(documentRequestContext.serviceName),
      eq(documentRequestContext.username),
    )
  }

  @Test
  fun `saves and flushes document`() {
    service.deleteDocument(documentUuid, documentRequestContext)

    verify(documentRepository).saveAndFlush(document)
  }
}
