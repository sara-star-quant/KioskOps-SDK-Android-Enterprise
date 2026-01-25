/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.debug

import com.peterz.kioskops.sdk.observability.CorrelationContext
import com.peterz.kioskops.sdk.observability.logging.Logger
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Debug-only OkHttp interceptor for network request/response logging.
 *
 * Logs HTTP request and response metadata with correlation IDs for debugging.
 * Sensitive headers (Authorization, cookies) are redacted.
 *
 * Security (BSI APP.4.4.A3): Does not log request/response bodies to
 * prevent sensitive data exposure. Only available in debug builds.
 *
 * @property logger Logger for output
 * @property redactedHeaders Headers to redact (values replaced with [REDACTED])
 * @property logRequestHeaders Whether to log request headers
 * @property logResponseHeaders Whether to log response headers
 *
 * @since 0.4.0
 */
@RequiresDebugBuild
class NetworkLoggingInterceptor(
  private val logger: Logger,
  private val redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
  private val logRequestHeaders: Boolean = true,
  private val logResponseHeaders: Boolean = true,
) : Interceptor {

  init {
    DebugUtils.requireDebugBuild()
  }

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val correlationId = CorrelationContext.correlationId

    // Log request
    val requestContentLength = request.body?.contentLength() ?: 0
    logger.d(TAG, ">>> ${request.method} ${request.url}", mapOf(
      "correlation_id" to correlationId,
      "content_length" to requestContentLength.toString(),
      "content_type" to (request.body?.contentType()?.toString() ?: "none"),
    ))

    if (logRequestHeaders) {
      logHeaders(">>> ", request.headers.toMultimap())
    }

    val startTime = System.nanoTime()

    val response: Response
    try {
      response = chain.proceed(request)
    } catch (e: Exception) {
      val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
      logger.e(TAG, "<<< FAILED ${request.url}", e, mapOf(
        "correlation_id" to correlationId,
        "duration_ms" to durationMs.toString(),
        "error" to e.message.orEmpty(),
      ))
      throw e
    }

    val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

    // Log response
    val responseContentLength = response.body?.contentLength() ?: -1
    logger.d(TAG, "<<< ${response.code} ${response.message} ${request.url}", mapOf(
      "correlation_id" to correlationId,
      "duration_ms" to durationMs.toString(),
      "content_length" to responseContentLength.toString(),
      "content_type" to (response.body?.contentType()?.toString() ?: "none"),
    ))

    if (logResponseHeaders) {
      logHeaders("<<< ", response.headers.toMultimap())
    }

    return response
  }

  private fun logHeaders(prefix: String, headers: Map<String, List<String>>) {
    headers.forEach { (name, values) ->
      val displayValue = if (shouldRedact(name)) {
        "[REDACTED]"
      } else {
        values.joinToString(", ")
      }
      logger.v(TAG, "$prefix$name: $displayValue")
    }
  }

  private fun shouldRedact(headerName: String): Boolean {
    return redactedHeaders.any { it.equals(headerName, ignoreCase = true) }
  }

  companion object {
    private const val TAG = "Network"

    /**
     * Default headers to redact for security.
     */
    val DEFAULT_REDACTED_HEADERS = setOf(
      "Authorization",
      "X-Api-Key",
      "X-Auth-Token",
      "Cookie",
      "Set-Cookie",
      "Proxy-Authorization",
      "WWW-Authenticate",
    )
  }
}
