package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.converter.JsonNodeConverter
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

  @Convert(converter = JsonNodeConverter::class)
  @ColumnTransformer(write = "?::jsonb")
  @Column(columnDefinition = "jsonb")
  val metadata: JsonNode,

  val supersededTime: LocalDateTime = LocalDateTime.now(),

  val supersededByServiceName: String,

  val supersededByUsername: String?,
)
