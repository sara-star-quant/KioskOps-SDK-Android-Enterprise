/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.util

import java.util.UUID

object Ids {
  fun uuid(): String = UUID.randomUUID().toString()
}
