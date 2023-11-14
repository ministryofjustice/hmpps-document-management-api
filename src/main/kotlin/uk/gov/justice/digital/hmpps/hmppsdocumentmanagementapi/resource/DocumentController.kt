package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentService
import java.util.UUID

@RestController
@RequestMapping("/documents", produces = [MediaType.APPLICATION_JSON_VALUE])
class DocumentController(
  private val documentService: DocumentService,
) {
  @GetMapping("/{documentUuid}")
  fun getDocument(@PathVariable documentUuid: UUID): Document {
    return documentService.getDocument(documentUuid)
  }

  @PostMapping("/{documentType}/{documentUuid}")
  fun uploadDocument(@PathVariable documentType: DocumentType, @PathVariable documentUuid: UUID): Document {
    throw NotImplementedError()
  }
}
