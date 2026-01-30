package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort.Direction
import org.springframework.data.jpa.domain.Specification
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

class DocumentSearchServiceTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentSearchSpecification: DocumentSearchSpecification = spy()
  private val eventService: EventService = mock()

  private val service = DocumentSearchService(documentRepository, documentSearchSpecification, eventService)

  private val warrantDocument = Document(
    documentType = DocumentType.HMCTS_WARRANT,
    filename = "warrant_for_remand",
    fileExtension = "pdf",
    fileSize = 48243,
    fileHash = "d58e3582afa99040e27b92b13c8f2280",
    mimeType = "application/pdf",
    metadata = ObjectMapper().readTree("{ \"prisonCode\": \"KPI\", \"prisonNumber\": \"A1234BC\" }"),
    createdByServiceName = "Remand and Sentencing",
    createdByUsername = "CREATED_BY_USER",
  )

  private val sarDocument = Document(
    documentType = DocumentType.SUBJECT_ACCESS_REQUEST_REPORT,
    filename = "subject_access_request_report",
    fileExtension = "pdf",
    fileSize = 63621,
    fileHash = "0e0396b7a0e931762c167abb7da85398",
    mimeType = "application/pdf",
    metadata = ObjectMapper().readTree("{ \"sarCaseReference\": \"SAR-1234\", \"prisonCode\": \"KPI\", \"prisonNumber\": \"A1234BC\" }"),
    createdByServiceName = "Manage Subject Access Requests",
    createdByUsername = "SAR_USER",
  )

  private val documentRequestContext = DocumentRequestContext(
    "Searched using service name",
    "PVI",
    "SEARCHED_BY_USERNAME",
  )

  @Test
  fun `search by document type only`() {
    val documentType = DocumentType.HMCTS_WARRANT

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(documentType, null), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by document type and metadata property`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }")

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(documentType, metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verify(documentSearchSpecification).metadataContains("prisonNumber", "A1234BC")
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by metadata property only`() {
    val metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }")

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verify(documentSearchSpecification).metadataContains("prisonNumber", "A1234BC")
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by multiple metadata properties`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = ObjectMapper().readTree("{ \"prisonCode\": \"KPI\", \"prisonNumber\": \"A1234BC\" }")

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(documentType, metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verify(documentSearchSpecification).metadataContains("prisonCode", "KPI")
    verify(documentSearchSpecification).metadataContains("prisonNumber", "A1234BC")
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `ignores non object metadata`() {
    val metadata = ObjectMapper().readTree("[ \"test\" ]")

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search uses page and page size`() {
    val request = DocumentSearchRequest(
      DocumentType.HMCTS_WARRANT,
      null,
      2,
      25,
    )

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(request, DocumentType.entries, documentRequestContext)

    verify(documentRepository).findAll(
      any<Specification<Document>>(),
      eq(
        PageRequest.of(request.page, request.pageSize)
          .withSort(request.orderByDirection, request.orderBy.property),
      ),
    )
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `default search is ordered by created time descending`() {
    val request = DocumentSearchRequest(DocumentType.HMCTS_WARRANT, null)

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(request, DocumentType.entries, documentRequestContext)

    verify(documentRepository).findAll(
      any<Specification<Document>>(),
      eq(
        PageRequest.of(request.page, request.pageSize)
          .withSort(Direction.DESC, "createdTime"),
      ),
    )
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `non default search ordering includes created time to resolve equal values`() {
    val request = DocumentSearchRequest(
      DocumentType.HMCTS_WARRANT,
      null,
      orderBy = DocumentSearchOrderBy.FILESIZE,
      orderByDirection = Direction.ASC,
    )

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(request, DocumentType.entries, documentRequestContext)

    verify(documentRepository).findAll(
      any<Specification<Document>>(),
      eq(
        PageRequest.of(request.page, request.pageSize)
          .withSort(request.orderByDirection, request.orderBy.property, "createdTime"),
      ),
    )
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `returns results`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }")
    val request = DocumentSearchRequest(documentType, metadata)

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>()))
      .thenReturn(PageImpl(listOf(warrantDocument)))

    val response = service.searchDocuments(request, DocumentType.entries, documentRequestContext)

    assertThat(response).isEqualTo(DocumentSearchResult(request, listOf(warrantDocument).toModels(), 1))
  }

  @Test
  fun `records event`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }")
    val request = DocumentSearchRequest(documentType, metadata, 0, 2)

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>()))
      .thenReturn(
        PageImpl(
          listOf(warrantDocument, sarDocument),
          PageRequest.of(request.page, request.pageSize)
            .withSort(request.orderByDirection, request.orderBy.property),
          5,
        ),
      )

    val documentSearchResult = service.searchDocuments(request, DocumentType.entries, documentRequestContext)

    verify(eventService).recordDocumentsSearchedEvent(
      eq(DocumentsSearchedEvent(documentSearchResult.request, documentSearchResult.results.size, documentSearchResult.totalResultsCount)),
      eq(documentRequestContext),
      any<Long>(),
    )
  }
}
