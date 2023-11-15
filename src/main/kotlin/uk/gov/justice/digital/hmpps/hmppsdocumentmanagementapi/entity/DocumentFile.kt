package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table
data class DocumentFile(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val documentFileId: Long = 0,

  val documentUuid: UUID,

  @Lob
  val fileData: ByteArray,
)
