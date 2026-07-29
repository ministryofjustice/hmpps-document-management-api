package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.CaseReferencesService

@RestController
@ServiceNameHeader
@ActiveCaseLoadIdHeader
@UsernameHeader
@RequestMapping("/court-documents/case-references", produces = [MediaType.APPLICATION_JSON_VALUE])
class CaseReferencesController(
  private val caseReferenceService: CaseReferencesService,
) {
  @ResponseStatus(HttpStatus.OK)
  @GetMapping("/{prisonerId}")
  @Operation(
    summary = "Get a list of case references from documents for a specific prisonerId",
    description = "Get a list of case references from documents for a specific prisonerId. " +
      "Can be used for further filtering using the GET /documents/search endpoint.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Document found",
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
  @PreAuthorize("hasAnyRole('$ROLE_DOCUMENT_READER', '$ROLE_DOCUMENT_ADMIN')")
  fun getCaseReferences(
    @PathVariable
    @Parameter(
      description = "prisoner unique identifier",
      required = true,
    )
    prisonerId: String,
  ) = caseReferenceService.getCaseReferences(prisonerId)
}
