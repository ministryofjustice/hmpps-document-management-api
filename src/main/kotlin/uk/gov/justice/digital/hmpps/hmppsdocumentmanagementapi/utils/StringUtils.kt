package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils

fun String.withoutPath() = Regex("[\\\\/]").findAll(this).lastOrNull().let {
  if (it == null) {
    this
  } else {
    this.substring(it.range.last + 1)
  }
}
