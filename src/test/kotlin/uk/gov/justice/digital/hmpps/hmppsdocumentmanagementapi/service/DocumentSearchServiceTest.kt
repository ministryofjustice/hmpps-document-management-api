package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

class DocumentSearchServiceTest {
  private val documentRepository: DocumentRepository = mock()
  private val documentSearchSpecification: DocumentSearchSpecification = spy()

  private val service = DocumentSearchService(documentRepository, documentSearchSpecification)

  private val document = Document(
    documentType = DocumentType.HMCTS_WARRANT,
    filename = "warrant_for_remand",
    fileExtension = "pdf",
    fileSize = 48243,
    fileHash = "d58e3582afa99040e27b92b13c8f2280",
    mimeType = "application/pdf",
    metadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"KPI\", \"prisonNumber\": \"A1234BC\" }"),
    createdByServiceName = "Remand and Sentencing",
    createdByUsername = "CREATED_BY_USER",
  )

  @Test
  fun `search by document type and metadata property`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    service.searchDocuments(DocumentSearchRequest(documentType, metadata), DocumentType.entries)

    verify(documentSearchSpecification).prisonCodeEquals(documentType)
    verify(documentSearchSpecification).metadataContains("prisonNumber", "A1234BC")
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by metadata property only`() {
    val documentType = null
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")

    service.searchDocuments(DocumentSearchRequest(documentType, metadata), DocumentType.entries)

    verify(documentSearchSpecification).prisonCodeEquals(null)
    verify(documentSearchSpecification).metadataContains("prisonNumber", "A1234BC")
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `search by multiple metadata properties`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"KPI\", \"prisonNumber\": \"A1234BC\" }")

    service.searchDocuments(DocumentSearchRequest(documentType, metadata), DocumentType.entries)

    verify(documentSearchSpecification).prisonCodeEquals(documentType)
    verify(documentSearchSpecification).metadataContains("prisonCode", "KPI")
    verify(documentSearchSpecification).metadataContains("prisonNumber", "A1234BC")
    verifyNoMoreInteractions(documentSearchSpecification)

    verify(documentRepository).findAll(any<Specification<Document>>())
    verifyNoMoreInteractions(documentRepository)
  }

  @Test
  fun `returns results`() {
    val documentType = DocumentType.HMCTS_WARRANT
    val metadata = JacksonUtil.toJsonNode("{ \"prisonNumber\": \"A1234BC\" }")
    val request = DocumentSearchRequest(documentType, metadata)

    whenever(documentRepository.findAll(any<Specification<Document>>())).thenReturn(listOf(document))

    val response = service.searchDocuments(request, DocumentType.entries)

    assertThat(response).isEqualTo(DocumentSearchResult(request, listOf(document).toModels()))
  }
}
