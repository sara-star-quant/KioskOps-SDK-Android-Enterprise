/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk

/**
 * Base exception for KioskOps SDK errors.
 */
open class KioskOpsException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause)

/**
 * Thrown when [KioskOpsSdk.get] is called before [KioskOpsSdk.init].
 */
class KioskOpsNotInitializedException :
  KioskOpsException("KioskOpsSdk.init() must be called before KioskOpsSdk.get()")

/**
 * Thrown when [KioskOpsSdk.init] is called more than once.
 * Call [KioskOpsSdk.get] to access the existing instance.
 */
class KioskOpsAlreadyInitializedException :
  KioskOpsException("KioskOpsSdk is already initialized. Use KioskOpsSdk.get() to access the existing instance.")
