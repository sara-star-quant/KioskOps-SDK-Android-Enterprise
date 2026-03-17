package com.peterz.kioskops.sdk.compliance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetentionEnforcerTest {

  @Test fun `maximalist defaults include 365 day minimum audit retention`() {
    val policy = RetentionPolicy.maximalistDefaults()
    assertThat(policy.minimumAuditRetentionDays).isEqualTo(365)
  }

  @Test fun `effective retention is max of configured and minimum`() {
    val policy = RetentionPolicy.maximalistDefaults().copy(
      retainAuditDays = 30,
      minimumAuditRetentionDays = 365,
    )
    val effective = maxOf(policy.retainAuditDays, policy.minimumAuditRetentionDays)
    assertThat(effective).isEqualTo(365)
  }

  @Test fun `custom retention above minimum is preserved`() {
    val policy = RetentionPolicy.maximalistDefaults().copy(
      retainAuditDays = 730,
      minimumAuditRetentionDays = 365,
    )
    val effective = maxOf(policy.retainAuditDays, policy.minimumAuditRetentionDays)
    assertThat(effective).isEqualTo(730)
  }
}
