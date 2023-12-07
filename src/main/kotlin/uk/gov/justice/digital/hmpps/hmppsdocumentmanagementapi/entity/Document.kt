package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.Document as DocumentModel

@Entity
@Table
@SQLRestriction("deleted_time IS NULL")
data class Document(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val documentId: Long = 0,

  val documentUuid: UUID = UUID.randomUUID(),

  @Enumerated(EnumType.STRING)
  val documentType: DocumentType,

  val filename: String,

  val fileExtension: String,

  val fileSize: Long,

  val fileHash: String,

  val mimeType: String,

  @Type(JsonType::class)
  @Column(columnDefinition = "jsonb")
  var metadata: JsonNode,

  val createdTime: LocalDateTime = LocalDateTime.now(),

  val createdByServiceName: String,

  val createdByUsername: String?,
) {
  var deletedTime: LocalDateTime? = null

  var deletedByServiceName: String? = null

  var deletedByUsername: String? = null

  @OneToMany(mappedBy = "document", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @OrderBy("supersededTime DESC")
  private val documentMetadataHistory: MutableList<DocumentMetadataHistory> = mutableListOf()

  fun documentMetadataHistory() = documentMetadataHistory

  fun documentFilename(): String {
    val sb = StringBuilder(filename)
    if (fileExtension.isNotEmpty()) {
      sb.append(".$fileExtension")
    }
    return sb.toString()
  }

  fun replaceMetadata(
    metadata: JsonNode,
    supersededTime: LocalDateTime = LocalDateTime.now(),
    supersededByServiceName: String,
    supersededByUsername: String?,
  ): DocumentMetadataHistory {
    val metadataHistory = DocumentMetadataHistory(
      document = this,
      metadata = this.metadata,
      supersededTime = supersededTime,
      supersededByServiceName = supersededByServiceName,
      supersededByUsername = supersededByUsername,
    )

    documentMetadataHistory.add(metadataHistory)

    this.metadata = metadata

    return metadataHistory
  }

  fun delete(deletedTime: LocalDateTime = LocalDateTime.now(), deletedByServiceName: String, deletedByUsername: String?) {
    this.deletedTime = deletedTime
    this.deletedByServiceName = deletedByServiceName
    this.deletedByUsername = deletedByUsername
  }

  fun toModel() =
    DocumentModel(
      documentUuid,
      documentType,
      documentFilename(),
      filename,
      fileExtension,
      fileSize,
      fileHash,
      mimeType,
      metadata,
      createdTime,
      createdByServiceName,
      createdByUsername,
    )
}

fun Collection<Document>.toModels() = map { it.toModel() }
