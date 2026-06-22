/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarastarquant.kioskops.sdk.crypto.FieldLevelEncryptor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Throughput of field-level AES-256-GCM encryption through the shipped
 * [FieldLevelEncryptor]. Uses an in-memory software key so the measurement
 * reflects the cipher, not AndroidKeyStore round-trips (see [SoftwareAesGcmProvider]).
 */
@RunWith(AndroidJUnit4::class)
class FieldEncryptionBenchmark {

  @get:Rule
  val benchmarkRule = BenchmarkRule()

  private val encryptor = FieldLevelEncryptor(SoftwareAesGcmProvider())
  private val fields = setOf("email", "phone", "ssn")
  private val payload =
    """{"email":"user@example.com","phone":"+15551234567","ssn":"123-45-6789","note":"clear"}"""
  private val encrypted = encryptor.encryptFields(payload, fields)

  @Test
  fun encryptFields() {
    benchmarkRule.measureRepeated {
      encryptor.encryptFields(payload, fields)
    }
  }

  @Test
  fun decryptFields() {
    benchmarkRule.measureRepeated {
      encryptor.decryptFields(encrypted)
    }
  }
}
