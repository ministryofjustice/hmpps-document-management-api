package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.integration.container

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object LocalStackContainer {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance by lazy { startLocalstackIfNotRunning() }

  fun setLocalStackProperties(localStackContainer: LocalStackContainer, registry: DynamicPropertyRegistry) {
    val localstackS3Url = localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString()
    val localstackSnsUrl = localStackContainer.getEndpointOverride(LocalStackContainer.Service.SNS).toString()
    val region = localStackContainer.region
    registry.add("hmpps.s3.localstackUrl") { localstackS3Url }
    registry.add("hmpps.s3.region") { region }
    registry.add("hmpps.sqs.localstackUrl") { localstackSnsUrl }
    registry.add("hmpps.sqs.region") { region }
  }

  private fun startLocalstackIfNotRunning(): LocalStackContainer? {
    if (localstackIsRunning()) return null
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    return LocalStackContainer(
      DockerImageName.parse("localstack/localstack:3.4.0"),
    ).apply {
      withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      withEnv("DEFAULT_REGION", "eu-west-2")
//      waitingFor(
//        Wait.forLogMessage(".*Running on.*", 1),
//      )
      start()
      followOutput(logConsumer)
    }
  }

  private fun localstackIsRunning(): Boolean = try {
    val serverSocket = ServerSocket(4566)
    serverSocket.localPort == 0
  } catch (e: IOException) {
    true
  }
}
