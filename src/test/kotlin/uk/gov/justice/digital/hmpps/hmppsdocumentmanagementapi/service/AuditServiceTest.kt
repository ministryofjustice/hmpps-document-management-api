package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.Feature
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.FeatureSwitches
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

class AuditServiceTest {
  private val hmppsQueueService: HmppsQueueService = mock()
  private val objectMapper = ObjectMapper()
  private val featureSwitches: FeatureSwitches = mock { on { isEnabled(any<Feature>(), any()) } doReturn true }
  private val auditQueue: HmppsQueue = mock()
  private val auditSqsClient: SqsAsyncClient = mock()
  private val auditQueueUrl = "audit-queue-url"

  private val service: AuditService = AuditService(hmppsQueueService, objectMapper, featureSwitches)

  private val document = mock<DocumentModel>()
  private val eventTime = LocalDateTime.now()

  private val documentRequestContext = DocumentRequestContext(
    "Service name",
    "KMI",
    "USERNAME",
  )

  @BeforeEach
  fun setUp() {
    whenever(auditQueue.sqsClient).thenReturn(auditSqsClient)
    whenever(auditQueue.queueUrl).thenReturn(auditQueueUrl)
    whenever(hmppsQueueService.findByQueueId("audit")).thenReturn(auditQueue)
  }

  @Test
  fun `does not send message when feature is disabled`() {
    featureSwitches.stub { on { isEnabled(Feature.HMPPS_AUDIT) } doReturn false }

    service.auditEvent(EventType.DOCUMENT_RETRIEVED, document, documentRequestContext, eventTime)

    verifyNoInteractions(auditSqsClient)
  }

  @Test
  fun `send message`() {
    val expectedAuditEvent = AuditService.AuditEvent(
      what = EventType.DOCUMENT_RETRIEVED.name,
      who = documentRequestContext.serviceName,
      service = documentRequestContext.serviceName,
      details = document.toJson(),
      `when` = DateTimeFormatter.ISO_INSTANT.format(eventTime.toInstant(ZoneOffset.UTC)),
    )

    service.auditEvent(
      EventType.DOCUMENT_RETRIEVED,
      document,
      DocumentRequestContext(
        documentRequestContext.serviceName,
        null,
        null,
      ),
      eventTime,
    )

    verify(auditSqsClient).sendMessage(
      SendMessageRequest.builder()
        .queueUrl(auditQueueUrl)
        .messageBody(expectedAuditEvent.toJson())
        .build(),
    )
  }

  @Test
  fun `uses service name when username is null`() {
    val expectedAuditEvent = AuditService.AuditEvent(
      what = EventType.DOCUMENT_RETRIEVED.name,
      who = documentRequestContext.username!!,
      service = documentRequestContext.serviceName,
      details = document.toJson(),
      `when` = DateTimeFormatter.ISO_INSTANT.format(eventTime.toInstant(ZoneOffset.UTC)),
    )

    service.auditEvent(EventType.DOCUMENT_RETRIEVED, document, documentRequestContext, eventTime)

    verify(auditSqsClient).sendMessage(
      SendMessageRequest.builder()
        .queueUrl(auditQueueUrl)
        .messageBody(expectedAuditEvent.toJson())
        .build(),
    )
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
}
