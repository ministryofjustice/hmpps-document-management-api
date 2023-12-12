package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.toModels
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.event.DocumentsSearchedEvent
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.DocumentSearchSpecification

@Service
@Transactional(readOnly = true)
class DocumentSearchService(
  private val documentRepository: DocumentRepository,
  private val documentSearchSpecification: DocumentSearchSpecification,
  private val eventService: EventService,
) {
  fun searchDocuments(
    request: DocumentSearchRequest,
    authorisedDocumentTypes: Collection<DocumentType>,
    documentRequestContext: DocumentRequestContext,
  ): DocumentSearchResult {
    val startTimeInMs = System.currentTimeMillis()

    request.documentType?.also {
      if (!authorisedDocumentTypes.contains(it)) {
        throw AccessDeniedException("Document type '$it' requires additional role")
      }
    }

    var spec = documentSearchSpecification.documentTypeEquals(request.documentType)

    request.metadata?.fields()?.forEach {
      spec = spec.and(documentSearchSpecification.metadataContains(it.key, it.value.asText()))
    }

    val pageRequest = PageRequest.of(request.page, request.pageSize)
      .withSort(request.orderByDirection, request.orderBy.property)
    val page = documentRepository.findAll(spec, pageRequest)
    val results = page.content.filter { authorisedDocumentTypes.contains(it.documentType) }

    return DocumentSearchResult(
      request,
      results.toModels(),
      page.totalElements,
    ).also {
      eventService.recordDocumentsSearchedEvent(
        DocumentsSearchedEvent(it.request, it.results.size),
        documentRequestContext,
        System.currentTimeMillis() - startTimeInMs,
      )
    }
  }
}
