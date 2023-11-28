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
import java.util.UUID

@Service
class DocumentFileService(
  hmppsS3Properties: HmppsS3Properties,
  private val s3Client: S3Client,
) {
  private lateinit var bucketName: String

  init {
    val bucket = hmppsS3Properties.buckets["document-management"]
    require(bucket != null) {
      "Configuration for S3 bucket 'document-management' not found. Check hmpps.s3 configuration"
    }

    bucketName = bucket.bucketName
  }

  fun saveDocumentFile(documentUuid: UUID, file: MultipartFile) {
    val request = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(documentUuid.toString())
      .build()

    s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))
  }

  fun getDocumentFile(documentUuid: UUID): ByteArray {
    val request = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(documentUuid.toString())
      .build()

    try {
      return s3Client.getObject(request).readAllBytes()
    } catch (e: NoSuchKeyException) {
      throw DocumentFileNotFoundException(documentUuid)
    }
  }
}
