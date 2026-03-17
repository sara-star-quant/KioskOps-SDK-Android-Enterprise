package com.peterz.kioskops.sdk.compliance

data class RetentionPolicy(
  val retainSentEventsDays: Int,
  val retainFailedEventsDays: Int,
  val retainTelemetryDays: Int,
  val retainAuditDays: Int,
  val retainLogsDays: Int,
  /** NIST AU-11: Minimum audit retention. Overrides retainAuditDays if higher. @since 0.5.0 */
  val minimumAuditRetentionDays: Int = 365,
) {
  companion object {
    fun maximalistDefaults() = RetentionPolicy(
      retainSentEventsDays = 7,
      retainFailedEventsDays = 14,
      retainTelemetryDays = 7,
      retainAuditDays = 30,
      retainLogsDays = 7,
      minimumAuditRetentionDays = 365,
    )
  }
}
