package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentFileNotFoundException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsS3Properties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.io.InputStream
import java.util.UUID

@Service
class DocumentFileService(
  private val hmppsS3Properties: HmppsS3Properties,
  private val s3Client: S3Client,
) {
  private fun getBucketName(documentType: DocumentType): String {
    val bucket = hmppsS3Properties.buckets[documentType.s3BucketName.value]
    require(bucket != null) {
      "Configuration for S3 bucket '$documentType' not found. Check hmpps.s3 configuration"
    }

    return bucket.bucketName
  }

  fun saveDocumentFile(documentUuid: UUID, file: MultipartFile, documentType: DocumentType) {
    val request = PutObjectRequest.builder()
      .bucket(getBucketName(documentType))
      .key(documentUuid.toString())
      .build()

    s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))
  }

  fun getDocumentFile(documentUuid: UUID, documentType: DocumentType): InputStream {
    val request = GetObjectRequest.builder()
      .bucket(getBucketName(documentType))
      .key(documentUuid.toString())
      .build()

    try {
      return s3Client.getObject(request)
    } catch (e: NoSuchKeyException) {
      throw DocumentFileNotFoundException(documentUuid)
    }
  }
}
