package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.VirusScanResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsScannedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository

class DocumentServiceScanDocumentTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentFileService: DocumentFileService = mock()
  private val eventService: EventService = mock()
  private val virusScanService: VirusScanService = mock()

  private val service =
    DocumentService(documentRepository, documentFileService, eventService, virusScanService)

  private val file = mock<MultipartFile>()
  private val size = 1234L
  private val documentRequestContext = DocumentRequestContext(
    "Uploaded using service name",
    "PVI",
    "UPLOADED_BY_USERNAME",
  )
  private val virusScanResult = mock<VirusScanResult>()

  @BeforeEach
  fun setUp() {
    whenever(file.size).thenReturn(size)
    whenever(file.inputStream).thenReturn("A".byteInputStream())
  }

  @Test
  fun `records event`() {
    service.scanDocument(file, documentRequestContext)
    val event = DocumentsScannedEvent(documentRequestContext, file.size)
    verify(eventService).recordDocumentScannedEvent(eq(event), any<Long>())
  }

  @Test
  fun `returns virus scan result`() {
    whenever(virusScanService.scan(eq(file.inputStream))).thenReturn(virusScanResult)

    assertThat(service.scanDocument(file, documentRequestContext)).isEqualTo(virusScanResult)
  }
}
