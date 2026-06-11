package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentPurgeProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentFileHashBackfillService
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentPurgeService

@RestController
@RequestMapping(value = ["/maintenance"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Hidden
class DocumentMaintenanceController(
  private val fileHashBackfillService: DocumentFileHashBackfillService,
  private val documentPurgeService: DocumentPurgeService,
  private val purge: DocumentPurgeProperties,
) {
  @PostMapping("/file-hash-backfill")
  @PreAuthorize("hasRole('$ROLE_DOCUMENT_ADMIN')")
  fun backfillFileHashes(
    @RequestParam(name = "confirm", required = true) confirm: String,
  ): Map<String, String> {
    require(confirm == BACKFILL_CONFIRM_TOKEN) {
      "Pass confirm=$BACKFILL_CONFIRM_TOKEN to run the file hash backfill"
    }
    val started = fileHashBackfillService.run()
    return mapOf("status" to if (started) "complete" else "already-running")
  }

  @PostMapping("/purge")
  @PreAuthorize("hasRole('$ROLE_DOCUMENT_ADMIN')")
  fun purgeDocuments(
    @RequestParam(name = "confirm", required = true) confirm: String,
  ): Map<String, Any> {
    require(confirm == PURGE_CONFIRM_TOKEN) {
      "Pass confirm=$PURGE_CONFIRM_TOKEN to run the document purge"
    }
    require(purge.documentTypes.isNotEmpty()) {
      "No purge document types configured; set document.purge.document-types and redeploy"
    }
    val result = documentPurgeService.purge(purge.documentTypes, purge.before)
    return mapOf(
      "started" to result.started,
      "purgedCount" to result.purgedCount,
      "s3FailureCount" to result.s3FailureCount,
    )
  }

  private companion object {
    const val BACKFILL_CONFIRM_TOKEN = "BACKFILL"
    const val PURGE_CONFIRM_TOKEN = "PURGE"
  }
}
