package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern

@Schema(
  description = "Sets the SHA-256 of a document's extracted content after upload.",
)
data class SetDocumentFileContentHashRequest(
  @Schema(
    description = "SHA-256 of the document's extracted content, as 64 character hex",
    example = "58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae",
  )
  @field:Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "fileContentHash must be a 64 character hex SHA-256.")
  val fileContentHash: String,
)
