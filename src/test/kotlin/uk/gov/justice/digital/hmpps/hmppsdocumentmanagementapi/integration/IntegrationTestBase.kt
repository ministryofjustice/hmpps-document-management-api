package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsS3Properties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.SERVICE_NAME
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.USERNAME
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException
import java.util.UUID

@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
@Sql("classpath:test_data/reset-database.sql")
@ExtendWith(OAuthExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  private lateinit var hmppsS3Properties: HmppsS3Properties

  @Autowired
  protected lateinit var s3Client: S3Client

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  fun HmppsSqsProperties.auditQueueConfig() =
    queues["audit"] ?: throw MissingQueueException("audit has not been loaded from configuration properties")

  private val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") ?: throw MissingQueueException("HmppsQueue audit not found") }

  protected val auditSqsClient by lazy { auditQueue.sqsClient }

  protected val auditQueueUrl by lazy { auditQueue.queueUrl }

  @AfterEach
  fun afterEach() {
    deleteAllDocumentsInS3()
    auditSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(auditQueueUrl).build()).get()
  }

  internal fun setAuthorisation(
    user: String? = null,
    client: String = CLIENT_ID,
    roles: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, client, roles)

  internal fun setDocumentContext(
    serviceName: String = "Test consuming service name",
    username: String? = "TEST_USERNAME",
  ): (HttpHeaders) -> Unit = {
    it.set(SERVICE_NAME, serviceName)
    it.set(USERNAME, username)
  }

  internal fun bucketName() = hmppsS3Properties.buckets["document-management"]!!.bucketName

  internal fun putDocumentInS3(documentUuid: UUID, fileResourcePath: String): ByteArray {
    val request = PutObjectRequest.builder()
      .bucket(bucketName())
      .key(documentUuid.toString())
      .build()
    val fileBytes = ClassPathResource(fileResourcePath).contentAsByteArray
    s3Client.putObject(request, RequestBody.fromBytes(fileBytes))
    return fileBytes
  }

  internal fun deleteAllDocumentsInS3() {
    val listObjectsResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName()).build())

    for (s3Object in listObjectsResponse.contents()) {
      val request = DeleteObjectRequest.builder()
        .bucket(bucketName())
        .key(s3Object.key())
        .build()
      s3Client.deleteObject(request)
    }
  }

  companion object {
    private val pgContainer = PostgresContainer.instance
    private val localStackContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
      }

      System.setProperty("aws.region", "eu-west-2")

      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }
}
