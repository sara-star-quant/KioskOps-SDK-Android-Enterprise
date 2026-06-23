/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.reliability

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots the real SDK with each compliance preset and runs the full consumer
 * flow. Catches "configured but never exercised" wiring bugs - this is the test
 * that would have caught the cuiDefaults SQLCipher launch crash.
 */
@RunWith(AndroidJUnit4::class)
class PresetSmokeInstrumentedTest : ReliabilitySdkTest() {

  @Test
  fun cuiDefaults_bootsAndRuns() = runPresetFlow(KioskOpsConfig.cuiDefaults(BASE_URL, LOCATION_ID))

  @Test
  fun cjisDefaults_bootsAndRuns() = runPresetFlow(KioskOpsConfig.cjisDefaults(BASE_URL, LOCATION_ID))

  @Test
  fun fedRampDefaults_bootsAndRuns() = runPresetFlow(KioskOpsConfig.fedRampDefaults(BASE_URL, LOCATION_ID))

  @Test
  fun gdprDefaults_bootsAndRuns() = runPresetFlow(KioskOpsConfig.gdprDefaults(BASE_URL, LOCATION_ID))

  @Test
  fun asdEssentialEightDefaults_bootsAndRuns() =
    runPresetFlow(KioskOpsConfig.asdEssentialEightDefaults(BASE_URL, LOCATION_ID))
}
