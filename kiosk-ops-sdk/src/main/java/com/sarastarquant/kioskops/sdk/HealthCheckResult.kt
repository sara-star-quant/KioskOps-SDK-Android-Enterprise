/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk

/**
 * Structured health status of the SDK.
 *
 * Returned by [KioskOpsSdk.healthCheck]. All fields reflect the current cached state;
 * no network calls are made.
 *
 * @since 0.7.0
 */
data class HealthCheckResult(
  val isInitialized: Boolean,
  val queueDepth: Long,
  val syncEnabled: Boolean,
  val lastHeartbeatReason: String?,
  val authProviderConfigured: Boolean,
  val encryptionEnabled: Boolean,
  val sdkVersion: String,
)
