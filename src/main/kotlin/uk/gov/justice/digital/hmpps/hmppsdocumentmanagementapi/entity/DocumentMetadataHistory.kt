package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

@Entity
@Table(name = "document_metadata_history")
data class DocumentMetadataHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val documentMetadataHistoryId: Long = 0,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", nullable = false)
  val document: Document,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val metadata: JsonNode,

  val supersededTime: LocalDateTime = LocalDateTime.now(),

  val supersededByServiceName: String,

  val supersededByUsername: String?,
)
