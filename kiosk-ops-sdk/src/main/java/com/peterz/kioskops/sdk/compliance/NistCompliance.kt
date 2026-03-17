/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.compliance

/**
 * Marks a class or function as implementing a specific NIST 800-53 control.
 *
 * Source-retention only; no runtime overhead.
 *
 * @property control The NIST control identifier (e.g., "SI-10", "SC-28").
 * @property title Human-readable control title.
 * @since 0.5.0
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Repeatable
annotation class NistControl(
  val control: String,
  val title: String,
)
