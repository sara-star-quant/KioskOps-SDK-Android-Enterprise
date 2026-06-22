/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

/**
 * Marks a test as conformance evidence for a specific compliance control.
 *
 * The control-to-test traceability matrix in `docs/compliance/traceability.md`
 * is generated from these annotations, and [ComplianceTraceabilityTest] fails
 * the build if the matrix is stale or if an annotation cites a control that the
 * framework's mapping document does not list.
 *
 * @property framework Framework name, matching a mapping document under
 *   `docs/compliance` (e.g. "NIST SP 800-171").
 * @property control Control identifier as written in that document (e.g. "3.13.11").
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ComplianceControl(
  val framework: String,
  val control: String,
)
