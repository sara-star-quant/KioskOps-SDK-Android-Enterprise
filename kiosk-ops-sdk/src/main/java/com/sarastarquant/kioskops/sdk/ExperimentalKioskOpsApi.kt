/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk

/**
 * Marks declarations that are experimental in KioskOps SDK.
 *
 * Experimental APIs may change or be removed in future releases without notice.
 * Callers must opt in with `@OptIn(ExperimentalKioskOpsApi::class)` to suppress the warning.
 *
 * @since 0.7.0
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "This KioskOps API is experimental and may change in future releases.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
)
annotation class ExperimentalKioskOpsApi
