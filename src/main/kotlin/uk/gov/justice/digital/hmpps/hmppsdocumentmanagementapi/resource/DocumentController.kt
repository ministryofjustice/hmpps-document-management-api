package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentService
import java.util.UUID

@RestController
@ServiceNameHeader
@UsernameHeader
@RequestMapping("/documents", produces = [MediaType.APPLICATION_JSON_VALUE])
class DocumentController(
  private val documentService: DocumentService,
) {
  @GetMapping("/{documentUuid}")
  @Operation(
    summary = "Get a document by its unique identifier",
    description =
    """
    Returns document properties and metadata associated with the document. The document file must be downloaded
    separately using the GET /documents/{documentUuid}/file endpoint.
    """,
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Document found",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role. Note that the required role can be document type dependent",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "The document associated with this unique identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PreAuthorize("hasAnyRole('DOCUMENT_READER', 'DOCUMENT_ADMIN')")
  fun getDocument(@PathVariable documentUuid: UUID) =
    documentService.getDocument(documentUuid)

  @GetMapping("/{documentUuid}/file", produces = [MediaType.APPLICATION_PDF_VALUE])
  @Operation(
    summary = "Download a document file by its unique identifier",
    description = "Returns document file binary with Content-Type and Content-Disposition headers.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Document found",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role. Note that the required role can be document type dependent",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "The document associated with this unique identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PreAuthorize("hasAnyRole('DOCUMENT_READER', 'DOCUMENT_ADMIN')")
  fun downloadDocumentFile(@PathVariable documentUuid: UUID): ResponseEntity<InputStreamResource> {
    val document = documentService.getDocument(documentUuid)
    val documentFile = null
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(document.mimeType))
      .contentLength(document.fileSize)
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${document.filename}.${document.fileExtension}\"")
      .body(documentFile)
  }

  @PostMapping("/{documentType}/{documentUuid}")
  @PreAuthorize("hasAnyRole('DOCUMENT_WRITER', 'DOCUMENT_ADMIN')")
  fun uploadDocument(
    @PathVariable documentType: DocumentType,
    @PathVariable documentUuid: UUID,
    @RequestParam file: MultipartFile,
    @RequestBody metadata: JsonNode
  ): Document {
    throw NotImplementedError()
  }
}
