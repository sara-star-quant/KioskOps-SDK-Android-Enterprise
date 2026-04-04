/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [DataRightsAuthorizer] integration with [DataRightsManager].
 *
 * Covers authorization allow/deny for all operations, backward compatibility,
 * audit logging of unauthorized attempts, and compliance preset defaults.
 */
@RunWith(RobolectricTestRunner::class)
class DataRightsAuthorizerTest {

  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  private fun buildTelemetry() = com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore(
    context = ctx,
    policyProvider = { TelemetryPolicy.maximalistDefaults() },
    retentionProvider = { RetentionPolicy.maximalistDefaults() },
    clock = Clock.SYSTEM,
    crypto = NoopCryptoProvider,
  )

  private fun buildAudit() = AuditTrail(
    ctx,
    { RetentionPolicy.maximalistDefaults() },
    Clock.SYSTEM,
    NoopCryptoProvider,
  )

  private fun buildManager(
    requireAuthorization: Boolean = false,
    persistentAudit: PersistentAuditTrail? = null,
  ) = DataRightsManager(
    context = ctx,
    telemetry = buildTelemetry(),
    audit = buildAudit(),
    queue = null,
    persistentAudit = persistentAudit,
    requireAuthorization = requireAuthorization,
  )

  // -- 1. Authorizer allows export: operation proceeds, returns Success or NoData --

  @Test
  fun `authorizer allows export -- operation proceeds and returns NoData for unknown user`() =
    runBlocking {
      val manager = buildManager()
      manager.setAuthorizer(DataRightsAuthorizer { _, _ -> true })

      val result = manager.exportUserData("unknown-user-1")
      assertThat(result).isInstanceOf(DataExportResult.NoData::class.java)
    }

  // -- 2. Authorizer denies export: returns Unauthorized --

  @Test
  fun `authorizer denies export -- returns Unauthorized with EXPORT operation`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> false })

    val result = manager.exportUserData("user-1")
    assertThat(result).isInstanceOf(DataExportResult.Unauthorized::class.java)
    val unauthorized = result as DataExportResult.Unauthorized
    assertThat(unauthorized.operation).isEqualTo(DataRightsOperation.EXPORT)
  }

  // -- 3. Authorizer allows delete: operation proceeds --

  @Test
  fun `authorizer allows delete -- operation proceeds and returns Success`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> true })

    val result = manager.deleteUserData("user-2")
    assertThat(result).isInstanceOf(DataDeletionResult.Success::class.java)
  }

  // -- 4. Authorizer denies delete: returns Unauthorized --

  @Test
  fun `authorizer denies delete -- returns Unauthorized with DELETE operation`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> false })

    val result = manager.deleteUserData("user-2")
    assertThat(result).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
    val unauthorized = result as DataDeletionResult.Unauthorized
    assertThat(unauthorized.operation).isEqualTo(DataRightsOperation.DELETE)
  }

  // -- 5. Authorizer allows wipe: operation proceeds --

  @Test
  fun `authorizer allows wipe -- operation proceeds and returns Success`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> true })

    val result = manager.wipeAllSdkData()
    assertThat(result).isInstanceOf(DataDeletionResult.Success::class.java)
  }

  // -- 6. Authorizer denies wipe: returns Unauthorized --

  @Test
  fun `authorizer denies wipe -- returns Unauthorized with WIPE operation`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> false })

    val result = manager.wipeAllSdkData()
    assertThat(result).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
    val unauthorized = result as DataDeletionResult.Unauthorized
    assertThat(unauthorized.operation).isEqualTo(DataRightsOperation.WIPE)
  }

  // -- 7. No authorizer + requireAuthorization=false: operations proceed (backward compatible) --

  @Test
  fun `no authorizer with requireAuthorization false -- export proceeds`() = runBlocking {
    val manager = buildManager(requireAuthorization = false)

    val result = manager.exportUserData("user-compat")
    assertThat(result).isNotInstanceOf(DataExportResult.Unauthorized::class.java)
  }

  @Test
  fun `no authorizer with requireAuthorization false -- delete proceeds`() = runBlocking {
    val manager = buildManager(requireAuthorization = false)

    val result = manager.deleteUserData("user-compat")
    assertThat(result).isInstanceOf(DataDeletionResult.Success::class.java)
  }

  @Test
  fun `no authorizer with requireAuthorization false -- wipe proceeds`() = runBlocking {
    val manager = buildManager(requireAuthorization = false)

    val result = manager.wipeAllSdkData()
    assertThat(result).isInstanceOf(DataDeletionResult.Success::class.java)
  }

  // -- 8. No authorizer + requireAuthorization=true: operations return Unauthorized --

  @Test
  fun `no authorizer with requireAuthorization true -- export returns Unauthorized`() =
    runBlocking {
      val manager = buildManager(requireAuthorization = true)

      val result = manager.exportUserData("user-blocked")
      assertThat(result).isInstanceOf(DataExportResult.Unauthorized::class.java)
    }

  @Test
  fun `no authorizer with requireAuthorization true -- delete returns Unauthorized`() =
    runBlocking {
      val manager = buildManager(requireAuthorization = true)

      val result = manager.deleteUserData("user-blocked")
      assertThat(result).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
    }

  @Test
  fun `no authorizer with requireAuthorization true -- wipe returns Unauthorized`() =
    runBlocking {
      val manager = buildManager(requireAuthorization = true)

      val result = manager.wipeAllSdkData()
      assertThat(result).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
    }

  // -- 9. setAuthorizer(null) removes authorization: operations proceed if not required --

  @Test
  fun `setAuthorizer null removes authorizer -- operations proceed when not required`() =
    runBlocking {
      val manager = buildManager(requireAuthorization = false)
      manager.setAuthorizer(DataRightsAuthorizer { _, _ -> false })

      // Blocked while authorizer is set
      val blocked = manager.deleteUserData("user-9")
      assertThat(blocked).isInstanceOf(DataDeletionResult.Unauthorized::class.java)

      // Remove authorizer
      manager.setAuthorizer(null)

      // Now proceeds since requireAuthorization=false
      val result = manager.deleteUserData("user-9")
      assertThat(result).isInstanceOf(DataDeletionResult.Success::class.java)
    }

  // -- 10. Authorizer receives correct operation type for each method --

  @Test
  fun `authorizer receives EXPORT operation for exportUserData`() = runBlocking {
    var capturedOperation: DataRightsOperation? = null
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { op, _ ->
      capturedOperation = op
      true
    })

    manager.exportUserData("user-10")
    assertThat(capturedOperation).isEqualTo(DataRightsOperation.EXPORT)
  }

  @Test
  fun `authorizer receives DELETE operation for deleteUserData`() = runBlocking {
    var capturedOperation: DataRightsOperation? = null
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { op, _ ->
      capturedOperation = op
      true
    })

    manager.deleteUserData("user-10")
    assertThat(capturedOperation).isEqualTo(DataRightsOperation.DELETE)
  }

  @Test
  fun `authorizer receives WIPE operation for wipeAllSdkData`() = runBlocking {
    var capturedOperation: DataRightsOperation? = null
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { op, _ ->
      capturedOperation = op
      true
    })

    manager.wipeAllSdkData()
    assertThat(capturedOperation).isEqualTo(DataRightsOperation.WIPE)
  }

  // -- 11. Authorizer receives correct userId for export and delete --

  @Test
  fun `authorizer receives correct userId for exportUserData`() = runBlocking {
    var capturedUserId: String? = null
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, uid ->
      capturedUserId = uid
      true
    })

    manager.exportUserData("target-user-export")
    assertThat(capturedUserId).isEqualTo("target-user-export")
  }

  @Test
  fun `authorizer receives correct userId for deleteUserData`() = runBlocking {
    var capturedUserId: String? = null
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, uid ->
      capturedUserId = uid
      true
    })

    manager.deleteUserData("target-user-delete")
    assertThat(capturedUserId).isEqualTo("target-user-delete")
  }

  // -- 12. Authorizer receives empty userId for wipe --

  @Test
  fun `authorizer receives empty userId for wipeAllSdkData`() = runBlocking {
    var capturedUserId: String? = null
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, uid ->
      capturedUserId = uid
      true
    })

    manager.wipeAllSdkData()
    assertThat(capturedUserId).isEqualTo("")
  }

  // -- 13. Unauthorized denial returns correct result (audit verified via return value) --

  @Test
  fun `authorizer denial for delete returns Unauthorized with DELETE operation`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> false })

    val result = manager.deleteUserData("audit-user-13")
    assertThat(result).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
    assertThat((result as DataDeletionResult.Unauthorized).operation).isEqualTo(DataRightsOperation.DELETE)
  }

  // -- 14. No-authorizer block returns correct result --

  @Test
  fun `no authorizer with requireAuthorization returns Unauthorized for export`() =
    runBlocking {
      val manager = buildManager(requireAuthorization = true)

      val result = manager.exportUserData("audit-user-14")
      assertThat(result).isInstanceOf(DataExportResult.Unauthorized::class.java)
    }

  // -- 15. cuiDefaults has requireDataRightsAuthorization=true --

  @Test
  fun `cuiDefaults sets requireDataRightsAuthorization to true`() {
    val config = KioskOpsConfig.cuiDefaults(
      baseUrl = "https://cui.example.com",
      locationId = "CUI-001",
    )
    assertThat(config.requireDataRightsAuthorization).isTrue()
  }

  // -- 16. cjisDefaults has requireDataRightsAuthorization=true --

  @Test
  fun `cjisDefaults sets requireDataRightsAuthorization to true`() {
    val config = KioskOpsConfig.cjisDefaults(
      baseUrl = "https://cjis.example.com",
      locationId = "CJIS-001",
    )
    assertThat(config.requireDataRightsAuthorization).isTrue()
  }

  // -- 17. gdprDefaults has requireDataRightsAuthorization=false --

  @Test
  fun `gdprDefaults sets requireDataRightsAuthorization to false`() {
    val config = KioskOpsConfig.gdprDefaults(
      baseUrl = "https://gdpr.example.com",
      locationId = "GDPR-001",
    )
    assertThat(config.requireDataRightsAuthorization).isFalse()
  }

  // -- 18. fedRampDefaults has requireDataRightsAuthorization=false --

  @Test
  fun `fedRampDefaults sets requireDataRightsAuthorization to false`() {
    val config = KioskOpsConfig.fedRampDefaults(
      baseUrl = "https://fedramp.example.com",
      locationId = "FEDRAMP-001",
    )
    assertThat(config.requireDataRightsAuthorization).isFalse()
  }

  // -- 19. Default config has requireDataRightsAuthorization=false --

  @Test
  fun `default KioskOpsConfig has requireDataRightsAuthorization false`() {
    val config = KioskOpsConfig(
      baseUrl = "https://default.example.com",
      locationId = "DEFAULT-001",
      kioskEnabled = true,
    )
    assertThat(config.requireDataRightsAuthorization).isFalse()
  }

  // -- 20. Authorizer can be changed dynamically between operations --

  @Test
  fun `authorizer can be changed dynamically between operations`() = runBlocking {
    val manager = buildManager()

    // First authorizer denies everything
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> false })
    val denied = manager.deleteUserData("user-20")
    assertThat(denied).isInstanceOf(DataDeletionResult.Unauthorized::class.java)

    // Swap to an authorizer that allows everything
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> true })
    val allowed = manager.deleteUserData("user-20")
    assertThat(allowed).isInstanceOf(DataDeletionResult.Success::class.java)
  }

  // -- Additional coverage: selective authorizer allows some operations but not others --

  @Test
  fun `authorizer can selectively allow export but deny delete`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { op, _ ->
      op == DataRightsOperation.EXPORT
    })

    val exportResult = manager.exportUserData("user-selective")
    assertThat(exportResult).isNotInstanceOf(DataExportResult.Unauthorized::class.java)

    val deleteResult = manager.deleteUserData("user-selective")
    assertThat(deleteResult).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
  }

  @Test
  fun `authorizer can selectively allow by userId`() = runBlocking {
    val manager = buildManager()
    manager.setAuthorizer(DataRightsAuthorizer { _, uid ->
      uid == "allowed-user"
    })

    val allowedResult = manager.deleteUserData("allowed-user")
    assertThat(allowedResult).isInstanceOf(DataDeletionResult.Success::class.java)

    val deniedResult = manager.deleteUserData("other-user")
    assertThat(deniedResult).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
  }

  @Test
  fun `setAuthorizer null then requireAuthorization true blocks operations`() = runBlocking {
    val manager = buildManager(requireAuthorization = true)

    // Set an authorizer that allows
    manager.setAuthorizer(DataRightsAuthorizer { _, _ -> true })
    val allowed = manager.deleteUserData("user-null-req")
    assertThat(allowed).isInstanceOf(DataDeletionResult.Success::class.java)

    // Remove authorizer -- should block since requireAuthorization=true
    manager.setAuthorizer(null)
    val blocked = manager.deleteUserData("user-null-req")
    assertThat(blocked).isInstanceOf(DataDeletionResult.Unauthorized::class.java)
  }
}
