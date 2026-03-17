package com.peterz.kioskops.sdk.compliance

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.crypto.NoopCryptoProvider
import com.peterz.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.peterz.kioskops.sdk.util.Clock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataRightsManagerTest {
  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test fun `exportUserData returns NoData for unknown user`() {
    val telemetry = EncryptedTelemetryStore(
      context = ctx,
      policyProvider = { TelemetryPolicy.maximalistDefaults() },
      retentionProvider = { RetentionPolicy.maximalistDefaults() },
      clock = Clock.SYSTEM,
      crypto = NoopCryptoProvider,
    )
    val audit = AuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, Clock.SYSTEM, NoopCryptoProvider)
    val manager = DataRightsManager(ctx, telemetry, audit)

    kotlinx.coroutines.runBlocking {
      val result = manager.exportUserData("nonexistent-user")
      assertThat(result).isInstanceOf(DataExportResult.NoData::class.java)
    }
  }

  @Test fun `deleteUserData succeeds for unknown user with zero deletions`() {
    val telemetry = EncryptedTelemetryStore(
      context = ctx,
      policyProvider = { TelemetryPolicy.maximalistDefaults() },
      retentionProvider = { RetentionPolicy.maximalistDefaults() },
      clock = Clock.SYSTEM,
      crypto = NoopCryptoProvider,
    )
    val audit = AuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, Clock.SYSTEM, NoopCryptoProvider)
    val manager = DataRightsManager(ctx, telemetry, audit)

    kotlinx.coroutines.runBlocking {
      val result = manager.deleteUserData("nonexistent-user")
      assertThat(result).isInstanceOf(DataDeletionResult.Success::class.java)
      val success = result as DataDeletionResult.Success
      assertThat(success.queueEventsDeleted).isEqualTo(0)
      assertThat(success.auditEventsDeleted).isEqualTo(0)
    }
  }

  @Test fun `resetSdkDeviceId returns new ID`() {
    val telemetry = EncryptedTelemetryStore(
      context = ctx,
      policyProvider = { TelemetryPolicy.maximalistDefaults() },
      retentionProvider = { RetentionPolicy.maximalistDefaults() },
      clock = Clock.SYSTEM,
      crypto = NoopCryptoProvider,
    )
    val audit = AuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, Clock.SYSTEM, NoopCryptoProvider)
    val manager = DataRightsManager(ctx, telemetry, audit)
    val newId = manager.resetSdkDeviceId()
    assertThat(newId).isNotEmpty()
  }
}
