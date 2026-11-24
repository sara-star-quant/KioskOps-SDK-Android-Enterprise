/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

/**
 * Content layout hashed into an audit chain link.
 *
 * - [FILE_V0]: file-based AuditTrail lines, `"ts|name|sortedFields|prevHash"`; id is not part of it.
 * - [V1]: legacy Room events, `"id|ts|name|fieldsJson|prevHash"`. Ambiguous when a value contains `|`.
 * - [V2]: each of id, ts, name, fieldsJson, prevHash as `"<utf8 byte length>:<value>"`, no delimiter.
 *
 * Existing hashes depend on these layouts byte for byte: never change one, add a new version instead.
 */
public enum class AuditHashFormat(public val version: Int) {
  FILE_V0(0),
  V1(1),
  V2(2),
  ;

  companion object {
    /** Format for a Room event's stored formatVersion, or null when the version is unknown. */
    public fun forStoredVersion(version: Int): AuditHashFormat? = when (version) {
      V1.version -> V1
      V2.version -> V2
      else -> null
    }
  }
}
