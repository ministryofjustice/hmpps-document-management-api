package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class DocumentTest {
  private val document =
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

  @Test
  fun `document contains replaced metadata`() {
    val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.metadata).isEqualTo(replacementMetadata)
  }

  @Test
  fun `metadata history contains original metadata`() {
    val originalMetadata = document.metadata
    val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.documentMetadataHistory().single().metadata).isEqualTo(originalMetadata)
  }

  @Test
  fun `metadata history contains audit information`() {
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
  fun `metadata history uses now as the default`() {
    val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.documentMetadataHistory().single().supersededTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
  }

  @Test
  fun `to model`() {
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
}