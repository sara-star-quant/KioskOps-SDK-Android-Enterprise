/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import kotlinx.serialization.Serializable

@Serializable
data class AuditEvent(
  val ts: Long,
  val name: String,
  val fields: Map<String, String> = emptyMap(),
  val prevHash: String,
  val hash: String,
)
