/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.fleet.config

/**
 * Event emitted when a configuration change occurs in [RemoteConfigManager].
 *
 * Observe via [com.sarastarquant.kioskops.sdk.KioskOpsSdk.configUpdateFlow].
 *
 * @since 0.9.0
 */
sealed class ConfigUpdateEvent {
  /** A new config version was applied. */
  data class Applied(val version: Long) : ConfigUpdateEvent()

  /** A config update was rejected by policy controls. */
  data class Rejected(val reason: String) : ConfigUpdateEvent()

  /** Config was rolled back from one version to another. */
  data class RolledBack(val fromVersion: Long, val toVersion: Long) : ConfigUpdateEvent()
}
