package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.awscore.exception.AwsServiceException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.repository.DocumentRepository
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service.DocumentFileService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UploadDocumentTransactionIntTest : IntegrationTestBase() {
  @SpyBean
  lateinit var repository: DocumentRepository

  @MockBean
  lateinit var fileService: DocumentFileService

  @Test
  fun `exception when storing document file rolls back transaction allowing retry`() {
    val documentUuid = UUID.randomUUID()

    whenever(fileService.saveDocumentFile(eq(documentUuid), any<MultipartFile>()))
      .thenThrow(AwsServiceException.builder().message("Test AWS Service Exception").build())

    val response = webTestClient.post()
      .uri("/documents/${DocumentType.HMCTS_WARRANT}/$documentUuid")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext("Uploaded via service name", "MDI", "UPLOADED_BY_USERNAME"))
      .exchange()
      .expectStatus().is5xxServerError
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    with(response) {
      assertThat(status).isEqualTo(500)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Unexpected error: Test AWS Service Exception")
      assertThat(developerMessage).isEqualTo("Test AWS Service Exception")
      assertThat(moreInfo).isNull()
    }

    // Confirm that the document was saved to the database
    verify(repository).saveAndFlush(argThat { this.documentUuid == documentUuid })

    // Confirm that the exception thrown when saving the document file caused the enclosing transaction to roll back
    // leaving no document with the supplied unique identifier in the database
    assertThat(repository.findByDocumentUuid(documentUuid)).isNull()
  }

  private fun documentMetadataMultipartBody() =
    MultipartBodyBuilder().apply {
      part("file", ClassPathResource("test_data/warrant-for-remand.pdf"))
      part(
        "metadata",
        JacksonUtil.toJsonNode("{ \"caseReferenceNumber\": \"T20231234\", \"prisonCode\": \"KMI\", \"prisonNumber\": \"A1234BC\" }"),
      )
    }.build()
}
