package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentFileHashBackfillService

@RestController
@RequestMapping(value = ["/maintenance"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Hidden
class DocumentMaintenanceController(
  private val fileHashBackfillService: DocumentFileHashBackfillService,
) {
  @PostMapping("/file-hash-backfill")
  @PreAuthorize("hasRole('$ROLE_DOCUMENT_ADMIN')")
  fun backfillFileHashes(
    @RequestParam(name = "confirm", required = true) confirm: String,
  ): Map<String, String> {
    require(confirm == CONFIRM_TOKEN) {
      "Pass confirm=$CONFIRM_TOKEN to run the file hash backfill"
    }
    val started = fileHashBackfillService.run()
    return mapOf("status" to if (started) "complete" else "already-running")
  }

  private companion object {
    const val CONFIRM_TOKEN = "BACKFILL"
  }
}
