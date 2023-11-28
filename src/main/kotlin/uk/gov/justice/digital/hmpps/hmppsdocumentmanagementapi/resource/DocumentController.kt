package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.DocumentSearchResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentSearchService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentService
import java.util.UUID

@RestController
@ServiceNameHeader
@UsernameHeader
@RequestMapping("/documents", produces = [MediaType.APPLICATION_JSON_VALUE])
class DocumentController(
  private val documentService: DocumentService,
  private val documentSearchService: DocumentSearchService,
) {
  @GetMapping("/{documentUuid}")
  @Operation(
    summary = "Get a document by its unique identifier",
    description = "Returns document properties and metadata associated with the document. The document file must be " +
      "downloaded separately using the GET /documents/{documentUuid}/file endpoint.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Document found",
        content = [Content(schema = Schema(implementation = Document::class))],
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
  fun getDocument(
    @PathVariable
    @Parameter(
      description = "Document unique identifier",
      required = true,
    )
    documentUuid: UUID,
  ) = documentService.getDocument(documentUuid)

  @GetMapping("/{documentUuid}/file", produces = [MediaType.APPLICATION_PDF_VALUE])
  @Operation(
    summary = "Download a document file by its unique identifier",
    description = "Returns document file binary with Content-Type and Content-Disposition headers.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Document file found",
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
        description = "The document file associated with this unique identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PreAuthorize("hasAnyRole('DOCUMENT_READER', 'DOCUMENT_ADMIN')")
  fun downloadDocumentFile(
    @PathVariable
    @Parameter(
      description = "Document unique identifier",
      required = true,
    )
    documentUuid: UUID,
  ): ResponseEntity<ByteArray> {
    val document = documentService.getDocument(documentUuid)
    val documentFile = documentService.getDocumentFile(documentUuid)
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(document.mimeType))
      .contentLength(documentFile.size.toLong())
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${document.filename}.${document.fileExtension}\"")
      .body(documentFile)
  }

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/{documentType}/{documentUuid}")
  @Operation(
    summary = "Upload a document file and associated metadata and store against a unique identifier",
    description = "Accepts a document file binary and associated metadata. Uses the supplied document type to apply any " +
      "validation rules and extra security then stores the file, creates and populates a document object with file " +
      "properties and supplied metadata and saves that document object. The document is associated with the client supplied " +
      "unique identifier. This identifier is used as an idempotency key and therefore cannot be reused once the upload operation is successful.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Document and associated metadata uploaded successfully",
        content = [Content(schema = Schema(implementation = Document::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
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
        responseCode = "409",
        description = "Conflict, the supplied document unique identifier is already present in the service",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PreAuthorize("hasAnyRole('DOCUMENT_WRITER', 'DOCUMENT_ADMIN')")
  fun uploadDocument(
    @PathVariable
    @Parameter(
      description = "The type of document being uploaded. This categorises the document and may enforce additional authentication and validation rules",
      required = true,
    )
    documentType: DocumentType,
    @PathVariable
    @Parameter(
      description = "Client supplied document unique identifier. A version 1 or version 4 (preferred) UUID. Used as an idempotency key preventing duplicate document uploads",
      required = true,
    )
    documentUuid: UUID,
    @RequestPart
    @Parameter(
      description = "File part of the multipart request",
      required = true,
    )
    file: MultipartFile,
    @RequestPart
    @Parameter(
      description = "The metadata describing the uploaded document. Should contain a person identifier e.g. prison number " +
        "or case reference number along with any other pertinent metadata. " +
        "The document type used will specify what metadata is required as a minimum",
      required = true,
      example =
      """
      {
        "prisonCode": "KMI",
        "prisonNumber": "C3456DE",
        "court": "Birmingham Magistrates",
        "warrantDate": "2023-11-14"
      }
      """,
    )
    metadata: String,
    request: HttpServletRequest,
  ) =
    documentService.uploadDocument(
      documentType,
      documentUuid,
      file,
      JacksonUtil.toJsonNode(metadata),
      request.getAttribute(DocumentRequestContext::class.simpleName) as DocumentRequestContext,
    )

  @ResponseStatus(HttpStatus.ACCEPTED)
  @PutMapping("/{documentUuid}/metadata")
  @Operation(
    summary = "Replace the metadata associated with a document",
    description = "Accepts JSON based metadata to associate with the document identified by the supplied unique identifier. " +
      "Applies authorisation and validation rules based on the type of document. If valid, the previous metadata will be stored " +
      "and the metadata associated with the document will be replaced with the supplied metadata.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "202",
        description = "Document metadata replaced successfully",
        content = [Content(schema = Schema(implementation = Document::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  @PreAuthorize("hasAnyRole('DOCUMENT_WRITER', 'DOCUMENT_ADMIN')")
  fun replaceDocumentMetadata(
    @PathVariable
    @Parameter(
      description = "Document unique identifier",
      required = true,
    )
    documentUuid: UUID,
    @RequestBody
    @Parameter(
      description = "The replacement metadata describing the document. Should contain a person identifier e.g. " +
        "prison number or case reference number along with any other pertinent metadata. " +
        "The document type used will specify what metadata is required as a minimum",
      required = true,
      example =
      """
      {
        "prisonCode": "KMI",
        "prisonNumber": "C3456DE",
        "court": "Birmingham Magistrates",
        "warrantDate": "2023-11-14"
      }
      """,
    )
    metadata: JsonNode,
    request: HttpServletRequest,
  ) =
    documentService.replaceDocumentMetadata(
      documentUuid,
      metadata,
      request.getAttribute(DocumentRequestContext::class.simpleName) as DocumentRequestContext,
    )

  @ResponseStatus(HttpStatus.ACCEPTED)
  @DeleteMapping("/{documentUuid}")
  @Operation(
    summary = "Delete a document by its unique identifier",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "202",
        description = "Document deleted",
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
  @PreAuthorize("hasAnyRole('DOCUMENT_WRITER', 'DOCUMENT_ADMIN')")
  fun deleteDocument(
    @PathVariable
    @Parameter(
      description = "Document unique identifier",
      required = true,
    )
    documentUuid: UUID,
  ) {
    throw NotImplementedError()
  }

  @ResponseStatus(HttpStatus.ACCEPTED)
  @PostMapping("/search")
  @Operation(
    summary = "Search for documents with matching metadata and optionally document type",
    description = "Uses the supplied metadata and optional document type to filter and return documents. " +
      "Documents will match if they are of the supplied type (optional) and/or their metadata contains all the supplied " +
      "properties and their values e.g. prisonCode = \"KMI\" AND prisonNumber = \"A1234BC\". Value matching is partial " +
      "and case insensitive so court = \"ham magis\" will match \"Birmingham Magistrates\".",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "202",
        description = "Search request accepted and results returned",
        content = [Content(schema = Schema(implementation = DocumentSearchResult::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  @PreAuthorize("hasAnyRole('DOCUMENT_READER', 'DOCUMENT_ADMIN')")
  fun searchDocuments(
    @Valid
    @RequestBody
    @Parameter(
      description = "The search parameters to use to filter documents",
      required = true,
    )
    request: DocumentSearchRequest,
  ) = documentSearchService.searchDocuments(request)
}
