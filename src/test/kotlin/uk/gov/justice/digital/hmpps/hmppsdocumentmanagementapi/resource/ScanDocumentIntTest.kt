package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.VirusScanStatus
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.VirusScanResult
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.ACTIVE_CASE_LOAD_ID_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.EVENT_TIME_MS_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.FILE_SIZE_METRIC_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.SERVICE_NAME_PROPERTY_KEY
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.telemetry.USERNAME_PROPERTY_KEY

class ScanDocumentIntTest : IntegrationTestBase() {
  private val serviceName = "Uploaded via service name"
  private val activeCaseLoadId = "RSI"
  private val username = "UPLOADED_BY_USERNAME"

  @Test
  fun `401 unauthorised`() {
    webTestClient.post()
      .uri("/documents/scan}")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - no roles`() {
    webTestClient.post()
      .uri("/documents/scan")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation())
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `403 forbidden - document reader`() {
    webTestClient.post()
      .uri("/documents/scan")
      .bodyValue(documentMetadataMultipartBody())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_READER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - no body`() {
    val response = webTestClient.post()
      .uri("/documents/scan")
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Current request is not a multipart request")
      assertThat(developerMessage).isEqualTo("Current request is not a multipart request")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `400 bad request - missing file`() {
    val response = webTestClient.post()
      .uri("/documents/scan")
      .bodyValue(MultipartBodyBuilder().apply { part("invalid", "test") }.build())
      .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
      .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    with(response!!) {
      assertThat(status).isEqualTo(400)
      assertThat(errorCode).isNull()
      assertThat(userMessage).isEqualTo("Validation failure: Required part 'file' is not present.")
      assertThat(developerMessage).isEqualTo("Required part 'file' is not present.")
      assertThat(moreInfo).isNull()
    }
  }

  @Test
  fun `response contains correct result when uploading file with virus`() {
    val response = webTestClient.scanDocument("eicar.txt")

    with(response) {
      assertThat(status).isEqualTo(VirusScanStatus.FAILED)
      assertThat(result).isEqualTo("stream: Win.Test.EICAR_HDB-1 FOUND")
      assertThat(signature).isEqualTo("Win.Test.EICAR_HDB-1")
    }
  }

  @Test
  fun `response contains correct result when uploading file without a virus`() {
    val response = webTestClient.scanDocument()

    with(response) {
      assertThat(status).isEqualTo(VirusScanStatus.PASSED)
      assertThat(result).isEqualTo("stream: OK")
      assertThat(signature).isNull()
    }
  }

  @Test
  fun `tracks event`() {
    webTestClient.scanDocument()

    val customEventProperties = argumentCaptor<Map<String, String>>()
    val customEventMetrics = argumentCaptor<Map<String, Double>>()
    verify(telemetryClient).trackEvent(eq(EventType.DOCUMENT_SCANNED.name), customEventProperties.capture(), customEventMetrics.capture())

    with(customEventProperties.firstValue) {
      assertThat(this[SERVICE_NAME_PROPERTY_KEY]).isEqualTo(serviceName)
      assertThat(this[ACTIVE_CASE_LOAD_ID_PROPERTY_KEY]).isEqualTo(activeCaseLoadId)
      assertThat(this[USERNAME_PROPERTY_KEY]).isEqualTo(username)
    }

    with(customEventMetrics.firstValue) {
      assertThat(this[EVENT_TIME_MS_METRIC_KEY]).isGreaterThan(0.0)
      assertThat(this[FILE_SIZE_METRIC_KEY]).isEqualTo(20688.0)
    }
  }

  private fun documentMetadataMultipartBody(file: String = "warrant-for-remand.pdf") = MultipartBodyBuilder().apply {
    part("file", ClassPathResource("test_data/$file"))
  }.build()

  private fun WebTestClient.scanDocument(
    file: String = "warrant-for-remand.pdf",
  ) = post()
    .uri("/documents/scan")
    .bodyValue(documentMetadataMultipartBody(file))
    .headers(setAuthorisation(roles = listOf(ROLE_DOCUMENT_WRITER)))
    .headers(setDocumentContext(serviceName, activeCaseLoadId, username))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(VirusScanResult::class.java)
    .returnResult().responseBody!!
}
