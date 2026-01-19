package com.peterz.kioskops.sdk.compliance

data class RetentionPolicy(
  val retainSentEventsDays: Int,
  val retainFailedEventsDays: Int,
  val retainTelemetryDays: Int,
  val retainAuditDays: Int,
  val retainLogsDays: Int,
) {
  companion object {
    fun maximalistDefaults() = RetentionPolicy(
      retainSentEventsDays = 7,
      retainFailedEventsDays = 14,
      retainTelemetryDays = 7,
      retainAuditDays = 30,
      retainLogsDays = 7
    )
  }
}
