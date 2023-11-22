package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity.Document
import java.util.UUID

class DocumentRepositoryTest {
  private val repository: DocumentRepository = mock()

  @Test
  fun `find or throw returns document when found`() {
    val documentUuid = UUID.randomUUID()
    val document = mock<Document>()

    whenever(repository.findByDocumentUuid(documentUuid)).thenReturn(document)

    assertThat(repository.findByDocumentUuidOrThrowNotFound(documentUuid)).isEqualTo(document)
  }

  @Test
  fun `find or throw throws exception when document not found`() {
    val documentUuid = UUID.randomUUID()

    whenever(repository.findByDocumentUuid(documentUuid)).thenReturn(null)

    assertThrows<EntityNotFoundException>(
      "Document with UUID '$documentUuid' not found",
    ) {
      repository.findByDocumentUuidOrThrowNotFound(documentUuid)
    }
  }
}
