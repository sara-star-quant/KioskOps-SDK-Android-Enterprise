package com.sarastarquant.kioskops.sdk.compliance

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import androidx.room.Room
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.sarastarquant.kioskops.sdk.util.Clock
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

  @Test fun `deleteUserData keeps the persistent audit chain verifiable`() {
    val db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).allowMainThreadQueries().build()
    AuditDatabase.setInstance(db)
    try {
      val telemetry = EncryptedTelemetryStore(
        context = ctx,
        policyProvider = { TelemetryPolicy.maximalistDefaults() },
        retentionProvider = { RetentionPolicy.maximalistDefaults() },
        clock = Clock.SYSTEM,
        crypto = NoopCryptoProvider,
      )
      val audit = AuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, Clock.SYSTEM, NoopCryptoProvider)
      val persistent = PersistentAuditTrail(ctx, { RetentionPolicy.maximalistDefaults() })
      val manager = DataRightsManager(ctx, telemetry, audit, persistentAudit = persistent)

      kotlinx.coroutines.runBlocking {
        persistent.record("a", userId = "u1")
        persistent.record("b", userId = "u2")
        persistent.record("c", userId = "u1")

        val result = manager.deleteUserData("u1")

        assertThat((result as DataDeletionResult.Success).auditEventsDeleted).isEqualTo(2)
        val verified = persistent.verifyChainIntegrity()
        assertThat(verified).isInstanceOf(ChainVerificationResult.Valid::class.java)
        val record = persistent.getEventsInRange(0, Long.MAX_VALUE).single { it.name == "user_data_deleted" }
        assertThat(record.userId).isNull()
        assertThat(record.fieldsJson).contains("queueDeleted")
        assertThat(record.fieldsJson).contains("auditDeleted")
        assertThat(record.fieldsJson).doesNotContain("u1")
      }
    } finally {
      db.close()
      AuditDatabase.setInstance(null)
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
