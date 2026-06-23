/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.reliability

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.compliance.DataDeletionResult
import com.sarastarquant.kioskops.sdk.compliance.DataExportResult
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the GDPR-style data-rights cascade under cuiDefaults (authorization
 * required, encrypted storage): enqueue as a user, export, delete, and confirm
 * the user's data is gone. Also confirms the authorization gate denies when the
 * authorizer rejects.
 */
@RunWith(AndroidJUnit4::class)
class DataRightsInstrumentedTest : ReliabilitySdkTest() {

  private val userId = "user-001"

  private fun bootCui() = KioskOpsSdk.init(ctx, { KioskOpsConfig.cuiDefaults(BASE_URL, LOCATION_ID) })

  @Test
  fun exportThenDelete_roundTrips() = runBlocking {
    val sdk = bootCui()
    try {
      sdk.setDataRightsAuthorizer { _, _ -> true }
      sdk.schemaRegistry.register(EVENT_TYPE, SCAN_SCHEMA)

      val enqueue = sdk.enqueueDetailed(EVENT_TYPE, """{"scan":"DR-1"}""", userId = userId)
      assertThat(enqueue).isInstanceOf(EnqueueResult.Accepted::class.java)

      val export = sdk.dataRights.exportUserData(userId)
      assertThat(export).isInstanceOf(DataExportResult.Success::class.java)
      assertThat((export as DataExportResult.Success).queueEventCount).isAtLeast(1)

      val delete = sdk.dataRights.deleteUserData(userId)
      assertThat(delete).isInstanceOf(DataDeletionResult.Success::class.java)
      assertThat((delete as DataDeletionResult.Success).queueEventsDeleted).isAtLeast(1)

      // The user's queue and audit data is gone. Device-level telemetry is not
      // user-scoped, so a re-export may still return Success carrying only that
      // telemetry; either way no queue/audit events remain for the user.
      when (val afterDelete = sdk.dataRights.exportUserData(userId)) {
        is DataExportResult.Success -> {
          assertThat(afterDelete.queueEventCount).isEqualTo(0)
          assertThat(afterDelete.auditEventCount).isEqualTo(0)
        }
        is DataExportResult.NoData -> Unit
        else -> throw AssertionError("unexpected re-export result: $afterDelete")
      }
    } finally {
      sdk.shutdown()
    }
  }

  @Test
  fun deniedAuthorizer_blocksExport() = runBlocking {
    val sdk = bootCui()
    try {
      sdk.setDataRightsAuthorizer { _, _ -> false }
      assertThat(sdk.dataRights.exportUserData(userId))
        .isInstanceOf(DataExportResult.Unauthorized::class.java)
    } finally {
      sdk.shutdown()
    }
  }
}
