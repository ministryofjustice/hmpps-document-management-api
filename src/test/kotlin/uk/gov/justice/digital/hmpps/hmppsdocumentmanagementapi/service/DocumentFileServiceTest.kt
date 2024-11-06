package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentFileNotFoundException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsS3Properties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

class DocumentFileServiceTest {
  private val documentManagementBucketName = "doc-management-bucket-name"
  private val prisonerImagesBucketName = "prisoner-images-bucket-name"
  private val distinguishingMarkImagesBucketName = "distinguishing-mark-images-bucket-name"

  private val hmppsS3Properties = HmppsS3Properties(
    buckets = mapOf(
      "document-management" to HmppsS3Properties.BucketConfig(bucketName = documentManagementBucketName),
      "prisoner-images" to HmppsS3Properties.BucketConfig(bucketName = prisonerImagesBucketName),
      "distinguishing-mark-images" to HmppsS3Properties.BucketConfig(bucketName = distinguishingMarkImagesBucketName),
    ),
  )

  private val s3Client = mock<S3Client>()

  private val service = DocumentFileService(hmppsS3Properties, s3Client)

  @Test
  fun `missing document-management bucket configuration throws exception`() {
    assertThrows<IllegalArgumentException>("Configuration for S3 bucket 'document-management' not found. Check hmpps.s3 configuration") {
      val serviceEmptyS3Properties = DocumentFileService(HmppsS3Properties(), s3Client)
      serviceEmptyS3Properties.saveDocumentFile(UUID.randomUUID(), mockFile(), DocumentType.PIC_CASE_DOCUMENTS)
    }
  }

  @ParameterizedTest
  @CsvSource(
    "HMCTS_WARRANT,doc-management-bucket-name",
    "SUBJECT_ACCESS_REQUEST_REPORT,doc-management-bucket-name",
    "EXCLUSION_ZONE_MAP,doc-management-bucket-name",
    "PIC_CASE_DOCUMENTS,doc-management-bucket-name",
    "PPUD_RECALL,doc-management-bucket-name",
    "PRISONER_PROFILE_PICTURE,prisoner-images-bucket-name",
    "DISTINGUISHING_MARK_IMAGE,distinguishing-mark-images-bucket-name",
  )
  fun `save document file uses bucket name from document type`(documentType: DocumentType, expectedBucketName: String) {
    val requestCaptor = argumentCaptor<PutObjectRequest>()
    whenever(s3Client.putObject(requestCaptor.capture(), any<RequestBody>())).thenReturn(mock<PutObjectResponse>())

    service.saveDocumentFile(UUID.randomUUID(), mockFile(), documentType)

    assertThat(requestCaptor.firstValue.bucket()).isEqualTo(expectedBucketName)
  }

  @Test
  fun `save document file uses unique identifier as object key`() {
    val documentUuid = UUID.randomUUID()

    val requestCaptor = argumentCaptor<PutObjectRequest>()
    whenever(s3Client.putObject(requestCaptor.capture(), any<RequestBody>())).thenReturn(mock<PutObjectResponse>())

    service.saveDocumentFile(documentUuid, mockFile(), DocumentType.PIC_CASE_DOCUMENTS)

    assertThat(requestCaptor.firstValue.key()).isEqualTo(documentUuid.toString())
  }

  @Test
  fun `save document file puts object in s3`() {
    service.saveDocumentFile(UUID.randomUUID(), mockFile(), DocumentType.PIC_CASE_DOCUMENTS)

    verify(s3Client).putObject(any<PutObjectRequest>(), any<RequestBody>())
  }

  @Test
  fun `get document file uses unique identifier as object key`() {
    val documentUuid = UUID.randomUUID()

    val requestCaptor = argumentCaptor<GetObjectRequest>()
    whenever(s3Client.getObject(requestCaptor.capture())).thenReturn(stubResponseBytes())

    service.getDocumentFile(documentUuid, DocumentType.PIC_CASE_DOCUMENTS)

    assertThat(requestCaptor.firstValue.key()).isEqualTo(documentUuid.toString())
  }

  @ParameterizedTest
  @CsvSource(
    "HMCTS_WARRANT,doc-management-bucket-name",
    "SUBJECT_ACCESS_REQUEST_REPORT,doc-management-bucket-name",
    "EXCLUSION_ZONE_MAP,doc-management-bucket-name",
    "PIC_CASE_DOCUMENTS,doc-management-bucket-name",
    "PPUD_RECALL,doc-management-bucket-name",
    "PRISONER_PROFILE_PICTURE,prisoner-images-bucket-name",
    "DISTINGUISHING_MARK_IMAGE,distinguishing-mark-images-bucket-name",
  )
  fun `get document file uses bucket name from document type`(documentType: DocumentType, expectedBucketName: String) {
    val requestCaptor = argumentCaptor<GetObjectRequest>()
    whenever(s3Client.getObject(requestCaptor.capture())).thenReturn(stubResponseBytes())

    service.getDocumentFile(UUID.randomUUID(), documentType)

    assertThat(requestCaptor.firstValue.bucket()).isEqualTo(expectedBucketName)
  }

  @Test
  fun `get document file gets object from s3`() {
    whenever(s3Client.getObject(any<GetObjectRequest>())).thenReturn(stubResponseBytes())

    service.getDocumentFile(UUID.randomUUID(), DocumentType.PIC_CASE_DOCUMENTS)

    verify(s3Client).getObject(any<GetObjectRequest>())
  }

  @Test
  fun `get document file throws DocumentFileNotFoundException when s3 client throws NoSuchKeyException`() {
    val documentUuid = UUID.randomUUID()

    whenever(s3Client.getObject(any<GetObjectRequest>())).thenThrow(NoSuchKeyException.builder().build())

    assertThrows<DocumentFileNotFoundException>("Document file with UUID '$documentUuid' not found.") {
      service.getDocumentFile(documentUuid, DocumentType.PIC_CASE_DOCUMENTS)
    }
  }

  private fun mockFile() =
    mock<MultipartFile>().apply {
      whenever(inputStream).thenReturn(mock<InputStream>())
      whenever(size).thenReturn(20688)
    }

  private fun stubResponseBytes() =
    ResponseInputStream(
      GetObjectResponse.builder().build(),
      AbortableInputStream.create(ByteArrayInputStream(ByteArray(10))),
    )
}
