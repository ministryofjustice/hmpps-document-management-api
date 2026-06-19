package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentHashingProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentFindByUuidsRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentRetrievedByUuidsEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentServiceFindByUuidsTest {
  private val documentRepository: DocumentRepository = mock()
  private val eventService: EventService = mock()
  private val virusScanService: VirusScanService = mock()

  private val service = DocumentService(
    documentRepository,
    mock(),
    eventService,
    virusScanService,
    DocumentHashingProperties(),
    mock(),
  )

  private val matchingDocument = Document(
    documentUuid = MATCHING_DOCUMENT_UUID,
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

  private val matchingDocument2 = Document(
    documentUuid = MATCHING_DOCUMENT_UUID_2,
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

  private val testDocuments: Map<UUID, Document> = mapOf(
    Pair(MATCHING_DOCUMENT_UUID, matchingDocument),
    Pair(MATCHING_DOCUMENT_UUID_2, matchingDocument2),
  )

  private val documentRequestContext = DocumentRequestContext(
    "Find by UUID using service name",
    "PVI",
    "RETRIEVED_BY_USERNAME",
  )

  @Test
  fun `test search by document UUIDs results when 1 matching document is found`() {
    val documentUuids = listOf(MATCHING_DOCUMENT_UUID)
    whenever(documentRepository.findByDocumentUuidIn(any<Collection<UUID>>()))
      .thenReturn(getMockedRepositoryResponse(documentUuids))
    val request = DocumentFindByUuidsRequest(documentUuids)

    val response: Collection<DocumentModel> = service.findByDocumentUuids(request, documentRequestContext)

    assertThat(response.size).isEqualTo(1)
    assertThat(response.filter { doc -> doc.documentUuid == MATCHING_DOCUMENT_UUID }.size).isEqualTo(1)
    assertThat(response.filter { doc -> documentUuids.contains(doc.documentUuid) }.size).isEqualTo(1)
  }

  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `test search by document UUIDs results when uuids are given`(documentUuids: Collection<UUID>, expectedResults: Int) {
    whenever(documentRepository.findByDocumentUuidIn(any<Collection<UUID>>()))
      .thenReturn(getMockedRepositoryResponse(documentUuids))
    val request = DocumentFindByUuidsRequest(documentUuids)

    val response: Collection<DocumentModel> = service.findByDocumentUuids(request, documentRequestContext)

    assertThat(response.size).isEqualTo(expectedResults)
    assertThat(response.filter { doc -> documentUuids.contains(doc.documentUuid) }.size).isEqualTo(expectedResults)
  }

  @ParameterizedTest
  @MethodSource("documentSearchByUuidsTestParameters")
  fun `records event`(documentUuids: Collection<UUID>, expectedResults: Int) {
    whenever(documentRepository.findByDocumentUuidIn(any<Collection<UUID>>()))
      .thenReturn(getMockedRepositoryResponse(documentUuids))
    val request = DocumentFindByUuidsRequest(documentUuids)

    val response: Collection<DocumentModel> = service.findByDocumentUuids(request, documentRequestContext)

    verify(eventService).recordDocumentRetrievedByUuidsEvent(
      eq(DocumentRetrievedByUuidsEvent(request, expectedResults)),
      eq(documentRequestContext),
      any<Long>(),
    )

    assertThat(response.size).isEqualTo(expectedResults)
  }

  private fun getMockedRepositoryResponse(documentUuids: Collection<UUID>): List<Document> = testDocuments
    .filterKeys { uuid -> documentUuids.contains(uuid) }.values.toList()

  companion object {
    val MATCHING_DOCUMENT_UUID: UUID = UUID.fromString("8980c409-465c-41a4-969d-affe0d9b9df7")
    val MATCHING_DOCUMENT_UUID_2: UUID = UUID.fromString("4fd5f7b0-eebf-4b69-9489-0cc48550e03b")
    val NOT_MATCHING_DOCUMENT_UUID: UUID = UUID.fromString("bdee9909-ba50-48d6-ad80-e8ecf6ffa912")

    @JvmStatic
    fun documentSearchByUuidsTestParameters() = listOf(
      Arguments.of(listOf(NOT_MATCHING_DOCUMENT_UUID), 0),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID), 1),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID_2), 1),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID, NOT_MATCHING_DOCUMENT_UUID), 1),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID, MATCHING_DOCUMENT_UUID_2), 2),
      Arguments.of(listOf(MATCHING_DOCUMENT_UUID, MATCHING_DOCUMENT_UUID_2, NOT_MATCHING_DOCUMENT_UUID), 2),
    )
  }
}
