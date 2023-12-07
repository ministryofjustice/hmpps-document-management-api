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

  private val replacementMetadata = JacksonUtil.toJsonNode("{ \"prisonCode\": \"RSI\", \"prisonNumber\": \"B2345CD\" }")

  @Test
  fun `document filename uses filename and extension`() {
    assertThat(document.documentFilename()).isEqualTo("${document.filename}.${document.fileExtension}")
  }

  @Test
  fun `document filename removes extension if it is empty`() {
    assertThat(document.copy(fileExtension = "").documentFilename()).isEqualTo(document.filename)
  }

  @Test
  fun `document contains replaced metadata`() {
    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.metadata).isEqualTo(replacementMetadata)
  }

  @Test
  fun `replace metadata adds to metadata history`() {
    document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(document.documentMetadataHistory()).hasSize(1)
  }

  @Test
  fun `metadata history references document`() {
    val metadataHistory = document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(metadataHistory.document).isEqualTo(document)
  }

  @Test
  fun `metadata history contains original metadata`() {
    val originalMetadata = document.metadata

    val metadataHistory = document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(metadataHistory.metadata).isEqualTo(originalMetadata)
  }

  @Test
  fun `metadata history contains audit information`() {
    val now = LocalDateTime.now()
    val serviceName = "Replaced metadata using service name"
    val username = "REPLACED_BY_USERNAME"

    val metadataHistory = document.replaceMetadata(replacementMetadata, now, serviceName, username)

    with(metadataHistory) {
      assertThat(supersededTime).isEqualTo(now)
      assertThat(supersededByServiceName).isEqualTo(serviceName)
      assertThat(supersededByUsername).isEqualTo(username)
    }

    assertThat(document.metadata).isEqualTo(replacementMetadata)
  }

  @Test
  fun `metadata history uses now as the default`() {
    val metadataHistory = document.replaceMetadata(replacementMetadata, supersededByServiceName = "Replaced metadata using service name", supersededByUsername = "REPLACED_BY_USERNAME")

    assertThat(metadataHistory.supersededTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
  }

  @Test
  fun `delete document stores audit information`() {
    val now = LocalDateTime.now()
    val serviceName = "Deleted using service name"
    val username = "DELETED_BY_USERNAME"

    document.delete(now, serviceName, username)

    with(document) {
      assertThat(deletedTime).isEqualTo(now)
      assertThat(deletedByServiceName).isEqualTo(serviceName)
      assertThat(deletedByUsername).isEqualTo(username)
    }
  }

  @Test
  fun `delete uses now as the default`() {
    document.delete(deletedByServiceName = "Deleted using service name", deletedByUsername = "DELETED_BY_USERNAME")

    assertThat(document.deletedTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
  }

  @Test
  fun `to model`() {
    assertThat(document.toModel()).isEqualTo(
      with(document) {
        DocumentModel(
          documentUuid,
          documentType,
          documentFilename(),
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
            documentFilename(),
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
