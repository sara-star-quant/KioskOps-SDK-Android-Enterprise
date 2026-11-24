/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.util

fun interface Clock {
  fun nowMs(): Long

  companion object {
    val SYSTEM: Clock = Clock { System.currentTimeMillis() }
  }
}
