package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
import tools.jackson.databind.JsonNode
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
    metadata = METADATA_PRISON_CODE_AND_NUMBER,
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
    metadata = METADATA_PRISON_AND_SAR_REFERENCE,
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

    service.searchDocuments(DocumentSearchRequest(listOf(documentType), null), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by document type and metadata property`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = METADATA_PRISON_NUMBER

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(listOf(documentType), metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verify(documentSearchSpecification).metadataContains(PRISON_NUMBER_KEY, PRISON_NUMBER_VALUE)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by metadata property only`() {
    val metadata = METADATA_PRISON_NUMBER

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verify(documentSearchSpecification).metadataContains(PRISON_NUMBER_KEY, PRISON_NUMBER_VALUE)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by multiple metadata properties`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = METADATA_PRISON_CODE_AND_NUMBER

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(listOf(documentType), metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verify(documentSearchSpecification).metadataContains(PRISON_CODE_KEY, PRISON_CODE_VALUE)
    verify(documentSearchSpecification).metadataContains(PRISON_NUMBER_KEY, PRISON_NUMBER_VALUE)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by file content hash only`() {
    val contentHash = "58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae"

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, null, fileContentHash = contentHash), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verify(documentSearchSpecification).fileContentHashEquals(contentHash)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by file hash only`() {
    val fileHash = "fffac8f1a93fabc8ad1629d255527c6ae12abfc5cc0921def588bfa2ce00b024"

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, null, fileHash = fileHash), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verify(documentSearchSpecification).fileHashEquals(fileHash)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by canonical filter combined with a document type`() {
    val documentType = DocumentType.HMCTS_WARRANT

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(listOf(documentType), null, canonical = true), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(setOf(documentType))
    verify(documentSearchSpecification).canonical(true)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `ignores non object metadata`() {
    val metadata = METADATA_NO_OBJECT

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, metadata), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @ParameterizedTest
  @MethodSource("getUnreadDocumentDateFromTestParameters")
  fun `test search request by document type, metadata property and metadata-exact property`(documentTypes: List<DocumentType>?, metadata: JsonNode?, metadataExact: JsonNode?) {
    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(documentTypes, metadata, metadataExact = metadataExact), DocumentType.entries, documentRequestContext)

    if (documentTypes.isNullOrEmpty()) {
      verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    } else {
      verify(documentSearchSpecification).documentTypeIn(documentTypes.toSet())
    }

    metadata?.propertyNames()?.toSet()?.forEach { property ->
      verify(documentSearchSpecification).metadataContains(property, metadata.get(property).asString())
    }

    metadataExact?.propertyNames()?.toSet()?.forEach { property ->
      verify(documentSearchSpecification).metadataEquals(property, metadataExact.get(property).asString())
    }

    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `test search request ignores non object metadata-exact`() {
    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>())).thenReturn(Page.empty())

    service.searchDocuments(DocumentSearchRequest(null, null, metadataExact = METADATA_NO_OBJECT), DocumentType.entries, documentRequestContext)

    verify(documentSearchSpecification).documentTypeIn(DocumentType.entries)
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>(), any<PageRequest>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search uses page and page size`() {
    val request = DocumentSearchRequest(
      listOf(DocumentType.HMCTS_WARRANT),
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
    val request = DocumentSearchRequest(listOf(DocumentType.HMCTS_WARRANT), null)

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
      listOf(DocumentType.HMCTS_WARRANT),
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
    val request = DocumentSearchRequest(listOf(documentType), metadata)

    whenever(documentRepository.findAll(any<Specification<Document>>(), any<PageRequest>()))
      .thenReturn(PageImpl(listOf(warrantDocument)))

    val response = service.searchDocuments(request, DocumentType.entries, documentRequestContext)

    assertThat(response).isEqualTo(DocumentSearchResult(request, listOf(warrantDocument).toModels(), 1))
  }

  @Test
  fun `records event`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = ObjectMapper().readTree("{ \"prisonNumber\": \"A1234BC\" }")
    val request = DocumentSearchRequest(listOf(documentType), metadata, 0, 2)

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

  private companion object {
    const val PRISON_NUMBER_KEY: String = "prisonNumber"
    const val PRISON_NUMBER_VALUE: String = "A1234BC"
    const val PRISON_CODE_KEY: String = "prisonCode"
    const val PRISON_CODE_VALUE: String = "KPI"
    const val SAR_REFERENCE_KEY: String = "sarCaseReference"
    const val SAR_REFERENCE_VALUE: String = "SAR-1234"

    val METADATA_PRISON_NUMBER: JsonNode = ObjectMapper().readTree("{ \"${PRISON_NUMBER_KEY}\": \"${PRISON_NUMBER_VALUE}\" }")
    val METADATA_PRISON_CODE_AND_NUMBER: JsonNode = ObjectMapper().readTree("{ \"${PRISON_CODE_KEY}\": \"${PRISON_CODE_VALUE}\", \"${PRISON_NUMBER_KEY}\": \"${PRISON_NUMBER_VALUE}\" }")
    val METADATA_NO_OBJECT: JsonNode = ObjectMapper().readTree("[ \"test\" ]")
    val METADATA_PRISON_AND_SAR_REFERENCE: JsonNode = ObjectMapper().readTree("{ \"${SAR_REFERENCE_KEY}\": \"${SAR_REFERENCE_VALUE}\", \"${PRISON_CODE_KEY}\": \"${PRISON_CODE_VALUE}\", \"${PRISON_NUMBER_KEY}\": \"${PRISON_NUMBER_VALUE}\" }")

    @JvmStatic
    fun getUnreadDocumentDateFromTestParameters() = listOf(
      Arguments.of(null, null, METADATA_PRISON_NUMBER),
      Arguments.of(listOf(DocumentType.HMCTS_WARRANT), null, METADATA_PRISON_NUMBER),
      Arguments.of(null, METADATA_PRISON_NUMBER, METADATA_PRISON_NUMBER),
      Arguments.of(listOf(DocumentType.HMCTS_WARRANT), null, METADATA_PRISON_NUMBER),
      Arguments.of(null, null, METADATA_PRISON_CODE_AND_NUMBER),
    )
  }
}
