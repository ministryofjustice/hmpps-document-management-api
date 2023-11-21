package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Transactional
class DocumentRepositoryIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: DocumentRepository

  @Sql("classpath:test_data/document-with-no-metadata-history-id-1.sql")
  @Test
  fun `retrieve document with no metadata history from database`() {
    val document = repository.findById(1L).orElseThrow()

    with(document) {
      assertThat(documentId).isEqualTo(1)
      assertThat(documentUuid).isEqualTo(UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37"))
      assertThat(documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
      assertThat(filename).isEqualTo("warrant_for_remand")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(48243)
      assertThat(fileHash).isEqualTo("d58e3582afa99040e27b92b13c8f2280")
      assertThat(mimeType).isEqualTo("application/pdf")
      assertThat(metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
      assertThat(metadata["prisonCode"].asText()).isEqualTo("KMI")
      assertThat(metadata).isEqualTo(JacksonUtil.toJsonNode("{\"prisonNumber\": \"A1234BC\", \"prisonCode\": \"KMI\"}"))
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo("Remand and Sentencing")
      assertThat(createdByUsername).isEqualTo("CREATED_BY_USER")
      assertThat(deletedTime).isNull()
      assertThat(deletedByServiceName).isNull()
      assertThat(deletedByUsername).isNull()
      assertThat(documentMetadataHistory()).isEmpty()
    }
  }

  @Sql("classpath:test_data/document-with-metadata-history-id-2.sql")
  @Test
  fun `retrieve document with metadata history from database`() {
    val document = repository.findById(2L).orElseThrow()

    with(document) {
      assertThat(documentId).isEqualTo(2)
      assertThat(documentUuid).isEqualTo(UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6a00"))
      assertThat(documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
      assertThat(filename).isEqualTo("warrant_for_remand")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(48243)
      assertThat(fileHash).isEqualTo("d58e3582afa99040e27b92b13c8f2280")
      assertThat(mimeType).isEqualTo("application/pdf")
      assertThat(metadata["prisonNumber"].asText()).isEqualTo("C3456DE")
      assertThat(metadata["prisonCode"].asText()).isEqualTo("KMI")
      assertThat(metadata).isEqualTo(JacksonUtil.toJsonNode("{\"prisonNumber\": \"C3456DE\", \"prisonCode\": \"KMI\"}"))
      assertThat(createdTime).isCloseTo(LocalDateTime.now().minusDays(3), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo("Remand and Sentencing")
      assertThat(createdByUsername).isEqualTo("CREATED_BY_USER")
      assertThat(deletedTime).isNull()
      assertThat(deletedByServiceName).isNull()
      assertThat(deletedByUsername).isNull()
    }

    with(document.documentMetadataHistory()) {
      assertThat(size).isEqualTo(2)
      with(get(0)) {
        assertThat(documentMetadataHistoryId).isEqualTo(2)
        assertThat(document.documentId).isEqualTo(2)
        assertThat(metadata["prisonNumber"].asText()).isEqualTo("B2345CD")
        assertThat(metadata["prisonCode"].asText()).isEqualTo("KMI")
        assertThat(metadata).isEqualTo(JacksonUtil.toJsonNode("{\"prisonNumber\": \"B2345CD\", \"prisonCode\": \"KMI\"}"))
        assertThat(supersededTime).isCloseTo(LocalDateTime.now().minusDays(1), within(3, ChronoUnit.SECONDS))
        assertThat(supersededByServiceName).isEqualTo("Remand and Sentencing")
        assertThat(supersededByUsername).isEqualTo("SUPERSEDED_BY_USER")
      }
      with(get(1)) {
        assertThat(documentMetadataHistoryId).isEqualTo(1)
        assertThat(document.documentId).isEqualTo(2)
        assertThat(metadata["prisonNumber"].asText()).isEqualTo("A1234BC")
        assertThat(metadata["prisonCode"].asText()).isEqualTo("KMI")
        assertThat(metadata).isEqualTo(JacksonUtil.toJsonNode("{\"prisonNumber\": \"A1234BC\", \"prisonCode\": \"KMI\"}"))
        assertThat(supersededTime).isCloseTo(LocalDateTime.now().minusDays(2), within(3, ChronoUnit.SECONDS))
        assertThat(supersededByServiceName).isEqualTo("Remand and Sentencing")
        assertThat(supersededByUsername).isEqualTo("SUPERSEDED_BY_USER")
      }
    }
  }

  @Sql("classpath:test_data/soft-deleted-document-id-3.sql")
  @Test
  fun `retrieve soft deleted document from database`() {
    assertThatThrownBy { repository.findById(3L).orElseThrow() }
      .isInstanceOf(NoSuchElementException::class.java)
  }

  @Test
  fun `persist document to database`() {
    val document = Document(
      documentType = DocumentType.HMCTS_WARRANT,
      filename = "warrant_for_remand",
      fileExtension = "pdf",
      fileSize = 48243,
      fileHash = "d58e3582afa99040e27b92b13c8f2280",
      mimeType = "application/pdf",
      metadata = JacksonUtil.toJsonNode("{\"prisonNumbers\": [\"A1234BC\"], \"prisonCodes\": [\"KMI\"]}"),
      createdByServiceName = "Remand and Sentencing",
      createdByUsername = "CREATED_BY_USER",
    )

    with(repository.saveAndFlush(document)) {
      assertThat(documentId).isEqualTo(1)
      assertThat(documentUuid).isEqualTo(documentUuid)
      assertThat(documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
      assertThat(filename).isEqualTo("warrant_for_remand")
      assertThat(fileExtension).isEqualTo("pdf")
      assertThat(fileSize).isEqualTo(48243)
      assertThat(fileHash).isEqualTo("d58e3582afa99040e27b92b13c8f2280")
      assertThat(mimeType).isEqualTo("application/pdf")
      assertThat(metadata["prisonNumbers"].single().asText()).isEqualTo("A1234BC")
      assertThat(metadata["prisonCodes"].single().asText()).isEqualTo("KMI")
      assertThat(metadata).isEqualTo(JacksonUtil.toJsonNode("{\"prisonNumbers\": [\"A1234BC\"], \"prisonCodes\": [\"KMI\"]}"))
      assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
      assertThat(createdByServiceName).isEqualTo("Remand and Sentencing")
      assertThat(createdByUsername).isEqualTo("CREATED_BY_USER")
      assertThat(deletedTime).isNull()
      assertThat(deletedByServiceName).isNull()
      assertThat(deletedByUsername).isNull()
      assertThat(documentMetadataHistory()).isEmpty()
    }
  }
}
