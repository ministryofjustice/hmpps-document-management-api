package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table
data class Document(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  val id: String,
)
