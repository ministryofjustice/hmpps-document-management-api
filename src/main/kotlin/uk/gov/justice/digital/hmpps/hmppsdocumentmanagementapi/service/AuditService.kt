package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentRequestContext
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.Feature
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.FeatureSwitches
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.EventType
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class AuditService(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  private val featureSwitches: FeatureSwitches,
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
    if (featureSwitches.isEnabled(Feature.HMPPS_AUDIT)) {
      sendToHmppsAudit(eventType, details, documentRequestContext, `when`)
    } else {
      log.debug("Not sending event type {} to HMPPS Audit as feature is disabled", eventType.name)
    }
  }

  private fun sendToHmppsAudit(
    eventType: EventType,
    details: Any,
    documentRequestContext: DocumentRequestContext,
    `when`: LocalDateTime,
  ) {
    val auditEvent = AuditEvent(
      what = eventType.name,
      who = documentRequestContext.username ?: documentRequestContext.serviceName,
      service = documentRequestContext.serviceName,
      details = details.toJson(),
      `when` = DateTimeFormatter.ISO_INSTANT.format(`when`.toInstant(ZoneOffset.UTC)),
    )

    log.debug("Sending event type {} to HMPPS Audit", eventType.name)

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

fun AuditService.AuditEvent.whenLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.parse(`when`), ZoneOffset.UTC)
