package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class AuditService(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") as HmppsQueue }
  private val auditSqsClient by lazy { auditQueue.sqsClient }
  private val auditQueueUrl by lazy { auditQueue.queueUrl }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun auditEvent(
    eventType: EventType,
    details: Any,
    documentRequestContext: DocumentRequestContext,
    `when`: LocalDateTime = LocalDateTime.now(),
  ) {
    val auditEvent = AuditEvent(
      what = eventType.name,
      who = documentRequestContext.username ?: documentRequestContext.serviceName,
      service = documentRequestContext.serviceName,
      details = details.toJson(),
      `when` = DateTimeFormatter.ISO_INSTANT.format(`when`.toInstant(ZoneOffset.UTC)),
    )
    log.debug("Audit {}", auditEvent)

    auditSqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(auditQueueUrl)
        .messageBody(auditEvent.toJson())
        .build(),
    )
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)

  data class AuditEvent(
    val what: String,
    val `when`: String,
    val who: String,
    val service: String,
    val details: String,
  )
}
