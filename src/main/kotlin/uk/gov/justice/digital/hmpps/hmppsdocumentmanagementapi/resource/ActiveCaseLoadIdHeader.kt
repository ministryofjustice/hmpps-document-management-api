package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
  name = ACTIVE_CASE_LOAD_ID,
  `in` = ParameterIn.HEADER,
  description = "The active case load id of the user interacting with the client service",
  required = false,
  content = [Content(schema = Schema(implementation = String::class))],
)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ActiveCaseLoadIdHeader
