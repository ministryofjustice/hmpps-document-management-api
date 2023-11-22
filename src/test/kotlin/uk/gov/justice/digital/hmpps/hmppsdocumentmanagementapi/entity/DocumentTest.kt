package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentTest {
  @Test
  fun `document contains replaced metadata`() {
    val document = document()
    val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.metadata).isEqualTo(replacementMetadata)
  }

  @Test
  fun `metadata history contains original metadata`() {
    val document = document()
    val originalMetadata = document.metadata
    val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.documentMetadataHistory().single().metadata).isEqualTo(originalMetadata)
  }

  @Test
  fun `metadata history contains audit information`() {
    val document = document()
    val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")
    val now = LocalDateTime.now()
    val serviceName = "Replaced metadata using service name"
    val username = "REPLACED_BY_USERNAME"

    document.replaceMetadata(replacementMetadata, now, serviceName, username)

    with(document.documentMetadataHistory().single()) {
      assertThat(supersededTime).isEqualTo(now)
      assertThat(supersededByServiceName).isEqualTo(serviceName)
      assertThat(supersededByUsername).isEqualTo(username)
    }

    assertThat(document.metadata).isEqualTo(replacementMetadata)
  }

  @Test
  fun `to model`() {
    val document = document()

    assertThat(document.toModel()).isEqualTo(
      with(document) {
        DocumentModel(
          documentUuid,
          documentType,
          filename,
          fileExtension,
          fileSize,
          fileHash,
          mimeType,
          metadata,
          createdTime,
          createdByServiceName,
          createdByUsername,
        )
      },
    )
  }

  @Test
  fun `to models`() {
    val document = document()

    assertThat(listOf(document).toModels()).isEqualTo(
      listOf(
        with(document) {
          DocumentModel(
            documentUuid,
            documentType,
            filename,
            fileExtension,
            fileSize,
            fileHash,
            mimeType,
            metadata,
            createdTime,
            createdByServiceName,
            createdByUsername,
          )
        },
      ),
    )
  }

  private fun document() =
    Document(
      documentId = 1,
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant_for_sentencing",
      fileExtension = "pdf",
      fileSize = 3876,
      fileHash = "d58e3582afa99040e27b92b13c8f2280",
      mimeType = "application/pdf",
      metadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"),
      createdByServiceName = "Remand and sentencing",
      createdByUsername = "CREATED_BY_USERNAME",
    )
}
