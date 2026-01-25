/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.debug

/**
 * Marks a class or function as only available in debug builds.
 *
 * Security (ISO 27001 A.14.2): Debug features must not be accessible
 * in production builds. Classes annotated with this annotation should
 * check BuildConfig.DEBUG before execution.
 *
 * Usage:
 * ```kotlin
 * @RequiresDebugBuild
 * class EventInspector {
 *   init {
 *     DebugUtils.requireDebugBuild()
 *   }
 * }
 * ```
 *
 * @since 0.4.0
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequiresDebugBuild

/**
 * Utility functions for debug build checks.
 *
 * @since 0.4.0
 */
object DebugUtils {

  /**
   * Whether the current build is a debug build.
   *
   * This should be set during SDK initialization based on BuildConfig.DEBUG.
   */
  @Volatile
  var isDebugBuild: Boolean = false
    internal set

  /**
   * Throws if not in a debug build.
   *
   * @throws IllegalStateException if not a debug build
   */
  fun requireDebugBuild() {
    if (!isDebugBuild) {
      throw IllegalStateException(
        "This feature is only available in debug builds. " +
        "Set observabilityPolicy.debugFeaturesEnabled = true in a debug build."
      )
    }
  }

  /**
   * Execute block only in debug builds.
   *
   * @param block Code to execute if debug build
   * @return Result of block or null in release builds
   */
  inline fun <T> debugOnly(block: () -> T): T? {
    return if (isDebugBuild) block() else null
  }

  /**
   * Execute block only in debug builds, with fallback.
   *
   * @param fallback Value to return in release builds
   * @param block Code to execute if debug build
   * @return Result of block or fallback
   */
  inline fun <T> debugOrElse(fallback: T, block: () -> T): T {
    return if (isDebugBuild) block() else fallback
  }
}
