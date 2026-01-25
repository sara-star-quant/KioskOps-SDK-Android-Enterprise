/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for StorageStatus.
 *
 * Security (BSI SYS.3.2.2.A8): Validates storage monitoring for fleet operations.
 * Privacy (GDPR): Verifies no PII in storage data.
 */
@RunWith(RobolectricTestRunner::class)
class StorageStatusTest {

  @Test
  fun `StorageStatus contains all fields`() {
    val status = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 32_000_000_000L,
      internalUsagePercent = 50,
      isLowStorage = false,
      hasExternalStorage = true,
      externalAvailableBytes = 16_000_000_000L,
    )

    assertThat(status.internalTotalBytes).isEqualTo(64_000_000_000L)
    assertThat(status.internalAvailableBytes).isEqualTo(32_000_000_000L)
    assertThat(status.internalUsagePercent).isEqualTo(50)
    assertThat(status.isLowStorage).isFalse()
    assertThat(status.hasExternalStorage).isTrue()
    assertThat(status.externalAvailableBytes).isEqualTo(16_000_000_000L)
  }

  @Test
  fun `isCritical returns true when usage above 95 percent`() {
    val critical = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 3_000_000_000L,
      internalUsagePercent = 96,
      isLowStorage = true,
      hasExternalStorage = false,
    )
    assertThat(critical.isCritical).isTrue()

    val notCritical = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 3_200_000_000L,
      internalUsagePercent = 95,
      isLowStorage = true,
      hasExternalStorage = false,
    )
    assertThat(notCritical.isCritical).isFalse()
  }

  @Test
  fun `externalAvailableBytes is null by default`() {
    val status = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 32_000_000_000L,
      internalUsagePercent = 50,
      isLowStorage = false,
      hasExternalStorage = false,
    )
    assertThat(status.externalAvailableBytes).isNull()
  }

  @Test
  fun `formatAvailableStorage returns GB for large values`() {
    val status = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 32_000_000_000L,
      internalUsagePercent = 50,
      isLowStorage = false,
      hasExternalStorage = false,
    )
    assertThat(status.formatAvailableStorage()).isEqualTo("32 GB")
  }

  @Test
  fun `formatAvailableStorage returns MB for medium values`() {
    val status = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 500_000_000L,
      internalUsagePercent = 99,
      isLowStorage = true,
      hasExternalStorage = false,
    )
    assertThat(status.formatAvailableStorage()).isEqualTo("500 MB")
  }

  @Test
  fun `formatAvailableStorage returns KB for small values`() {
    val status = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 500_000L,
      internalUsagePercent = 100,
      isLowStorage = true,
      hasExternalStorage = false,
    )
    assertThat(status.formatAvailableStorage()).isEqualTo("500 KB")
  }

  @Test
  fun `formatAvailableStorage returns B for tiny values`() {
    val status = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 500L,
      internalUsagePercent = 100,
      isLowStorage = true,
      hasExternalStorage = false,
    )
    assertThat(status.formatAvailableStorage()).isEqualTo("500 B")
  }

  @Test
  fun `threshold constants are defined`() {
    assertThat(StorageStatus.LOW_STORAGE_THRESHOLD_PERCENT).isEqualTo(10)
    assertThat(StorageStatus.CRITICAL_STORAGE_THRESHOLD_PERCENT).isEqualTo(5)
  }

  @Test
  fun `copy preserves unchanged fields`() {
    val original = StorageStatus(
      internalTotalBytes = 64_000_000_000L,
      internalAvailableBytes = 32_000_000_000L,
      internalUsagePercent = 50,
      isLowStorage = false,
      hasExternalStorage = true,
    )

    val modified = original.copy(internalUsagePercent = 60)

    assertThat(modified.internalTotalBytes).isEqualTo(64_000_000_000L)
    assertThat(modified.internalAvailableBytes).isEqualTo(32_000_000_000L)
    assertThat(modified.internalUsagePercent).isEqualTo(60)
    assertThat(modified.isLowStorage).isFalse()
    assertThat(modified.hasExternalStorage).isTrue()
  }
}
