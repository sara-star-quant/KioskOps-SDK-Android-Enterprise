package com.peterz.kioskops.sdk.transport

import okhttp3.Request

/**
 * Host app injects authentication/authorization without the SDK dictating how.
 *
 * Examples:
 * - Bearer tokens
 * - mTLS headers (when paired with custom OkHttpClient)
 * - Request signing
 */
fun interface AuthProvider {
  fun apply(builder: Request.Builder)
}
