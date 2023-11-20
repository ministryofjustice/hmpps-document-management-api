package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.resource

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
  name = SERVICE_NAME,
  `in` = ParameterIn.HEADER,
  description = "Client supplied name of the calling service. This should be the product name of the service as listed in the developer portal",
  required = true,
  content = [Content(schema = Schema(implementation = String::class))],
)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ServiceNameHeader
