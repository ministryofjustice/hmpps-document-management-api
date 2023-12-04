package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.time.LocalDateTime
import java.util.UUID

@Schema(
  description = "Document properties and metadata associated with the document. The document file must be downloaded " +
    "separately using the GET /documents/{documentUuid}/file endpoint.",
)
data class Document(
  @Schema(
    description = "The unique identifier assigned to the document",
    example = "8cdadcf3-b003-4116-9956-c99bd8df6a00",
  )
  val documentUuid: UUID,

  @Schema(
    description = "The type or category of the document within HMPPS",
    example = "HMCTS_WARRANT",
  )
  val documentType: DocumentType,

  @Schema(
    description = "The generated filename the document file will be given when downloaded. The format of this filename " +
      "can be document type specific and may include type information. the filename of the document file when it was uploaded " +
      "as well as relevant metadata e.g. case reference or prison number",
    example = "warrant_for_remand",
  )
  val documentFilename: String,

  @Schema(
    description = "The filename of the document file when it was uploaded with the file extension removed",
    example = "warrant_for_remand",
  )
  val filename: String,

  @Schema(
    description = "The file extension of the document file",
    example = "pdf",
  )
  val fileExtension: String,

  @Schema(
    description = "The file size in bytes of the document file",
    example = "48243",
  )
  val fileSize: Long,

  @Schema(
    description = "The md5 hash of the document file",
    example = "d58e3582afa99040e27b92b13c8f2280",
  )
  val fileHash: String,

  @Schema(
    description = "The application/pdf of the document file",
    example = "pdf",
  )
  val mimeType: String,

  @Schema(
    description = "JSON structured metadata associated with the document. May contain prison codes, prison numbers, " +
      "dates, tags etc. and the properties available will be defined by the document's type.",
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
  val metadata: JsonNode,

  @Schema(
    description = "The date and time this document was uploaded and created in the service",
  )
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val createdTime: LocalDateTime,

  @Schema(
    description = "The name of the service that was used to upload the document file",
    example = "Remand and Sentencing",
  )
  val createdByServiceName: String,

  @Schema(
    description = "The username of the user that uploaded the document file",
    example = "AAA01U",
  )
  val createdByUsername: String?,
)
