package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration

import com.microsoft.applicationinsights.TelemetryClient
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsS3Properties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.S3BucketName
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.ClamAVContainer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.ACTIVE_CASE_LOAD_ID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.SERVICE_NAME
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource.USERNAME
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
@Sql("classpath:test_data/reset-database.sql")
@ExtendWith(OAuthExtension::class)
@AutoConfigureWebTestClient
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

  @MockitoBean
  protected lateinit var telemetryClient: TelemetryClient

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  private val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") ?: throw MissingQueueException("HmppsQueue audit not found") }

  protected val auditSqsClient by lazy { auditQueue.sqsClient }

  protected val auditQueueUrl by lazy { auditQueue.queueUrl }

  @AfterEach
  fun afterEach() {
    deleteAllDocumentsInAllS3()
    auditSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(auditQueueUrl).build()).get()
  }

  internal fun setAuthorisation(
    user: String? = null,
    client: String = CLIENT_ID,
    roles: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, client, roles)

  internal fun setDocumentContext(
    serviceName: String = "Test consuming service name",
    activeCaseLoadId: String = "MDI",
    username: String? = "TEST_USERNAME",
  ): (HttpHeaders) -> Unit = {
    it.set(SERVICE_NAME, serviceName)
    it.set(ACTIVE_CASE_LOAD_ID, activeCaseLoadId)
    it.set(USERNAME, username)
  }

  internal fun bucketName(bucketIdentifier: String) = hmppsS3Properties.buckets[bucketIdentifier]!!.bucketName

  internal fun putDocumentInS3(documentUuid: UUID, fileResourcePath: String, bucketIdentifier: String): ByteArray {
    val request = PutObjectRequest.builder()
      .bucket(bucketName(bucketIdentifier))
      .key(documentUuid.toString())
      .build()
    val fileBytes = ClassPathResource(fileResourcePath).contentAsByteArray
    s3Client.putObject(request, RequestBody.fromBytes(fileBytes))
    return fileBytes
  }

  internal fun deleteAllDocumentsInS3Bucket(bucketIdentifier: String) {
    val bucketName = bucketName(bucketIdentifier)
    val listObjectsResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build())

    for (s3Object in listObjectsResponse.contents()) {
      val request = DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Object.key())
        .build()
      s3Client.deleteObject(request)
    }
  }

  internal fun deleteAllDocumentsInAllS3() {
    S3BucketName.entries.stream().forEach { bucketEnum ->
      deleteAllDocumentsInS3Bucket(bucketEnum.value)
    }
  }

  companion object {
    private val pgContainer = PostgresContainer.instance
    private val localStackContainer = LocalStackContainer.instance
    private val clamAVContainer = ClamAVContainer.instance

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
      clamAVContainer?.run {
        registry.add("hmpps.clamav.port") { clamAVContainer.firstMappedPort }
      }
    }
  }
}

fun Document.assertIsDocumentWithNoMetadataHistoryId1(
  metadata: JsonNode = ObjectMapper().readTree("{\"prisonNumber\": \"A1234BC\", \"prisonCode\": \"KMI\"}"),
) {
  assertThat(documentUuid).isEqualTo(UUID.fromString("f73a0f91-2957-4224-b477-714370c04d37"))
  assertThat(documentType).isEqualTo(DocumentType.HMCTS_WARRANT)
  assertThat(filename).isEqualTo("warrant_for_remand")
  assertThat(fileExtension).isEqualTo("pdf")
  assertThat(fileSize).isEqualTo(20688)
  assertThat(fileHash).isEqualTo("d58e3582afa99040e27b92b13c8f2280")
  assertThat(mimeType).isEqualTo("application/pdf")
  assertThat(this.metadata).isEqualTo(metadata)
  assertThat(createdTime).isCloseTo(LocalDateTime.now(), within(3, ChronoUnit.SECONDS))
  assertThat(createdByServiceName).isEqualTo("Remand and Sentencing")
  assertThat(createdByUsername).isEqualTo("CREATED_BY_USER")
}
