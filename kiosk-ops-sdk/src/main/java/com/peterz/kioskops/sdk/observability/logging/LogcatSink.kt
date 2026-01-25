/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

import android.util.Log
import com.peterz.kioskops.sdk.observability.LogLevel

/**
 * Logging sink that outputs to Android Logcat.
 *
 * Formats log entries with structured fields appended to the message
 * for visibility in Logcat viewers.
 *
 * @property minLevel Minimum log level to output
 * @property tagPrefix Prefix for log tags (e.g., "KioskOps")
 *
 * @since 0.4.0
 */
class LogcatSink(
  override val minLevel: LogLevel = LogLevel.DEBUG,
  private val tagPrefix: String = "KioskOps",
) : LoggingSink {

  override fun emit(entry: LogEntry) {
    val tag = formatTag(entry.tag)
    val message = formatMessage(entry)

    when (entry.level) {
      LogLevel.VERBOSE -> {
        if (entry.throwable != null) {
          Log.v(tag, message, entry.throwable)
        } else {
          Log.v(tag, message)
        }
      }
      LogLevel.DEBUG -> {
        if (entry.throwable != null) {
          Log.d(tag, message, entry.throwable)
        } else {
          Log.d(tag, message)
        }
      }
      LogLevel.INFO -> {
        if (entry.throwable != null) {
          Log.i(tag, message, entry.throwable)
        } else {
          Log.i(tag, message)
        }
      }
      LogLevel.WARN -> {
        if (entry.throwable != null) {
          Log.w(tag, message, entry.throwable)
        } else {
          Log.w(tag, message)
        }
      }
      LogLevel.ERROR -> {
        if (entry.throwable != null) {
          Log.e(tag, message, entry.throwable)
        } else {
          Log.e(tag, message)
        }
      }
    }
  }

  private fun formatTag(tag: String): String {
    return if (tagPrefix.isEmpty()) {
      tag.take(MAX_TAG_LENGTH)
    } else {
      "$tagPrefix/$tag".take(MAX_TAG_LENGTH)
    }
  }

  private fun formatMessage(entry: LogEntry): String {
    return buildString {
      append(entry.message)

      // Append key fields for visibility
      val keyFields = entry.fields.filterKeys { key ->
        key in VISIBLE_FIELDS
      }
      if (keyFields.isNotEmpty()) {
        append(" [")
        append(keyFields.entries.joinToString(", ") { "${it.key}=${it.value}" })
        append("]")
      }
    }
  }

  companion object {
    // Android Logcat tag limit
    private const val MAX_TAG_LENGTH = 23

    // Fields to include in Logcat output (keep concise)
    private val VISIBLE_FIELDS = setOf(
      "correlation_id",
      "operation",
      "duration_ms",
      "event_type",
      "error_code",
    )
  }
}
