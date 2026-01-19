package com.peterz.kioskops.sdk.transport

import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.logging.RingLog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpTransport(
  private val client: OkHttpClient,
  private val json: Json,
  private val logs: RingLog,
  private val authProvider: AuthProvider? = null
) : Transport {

  private val contentType = "application/json; charset=utf-8".toMediaType()

  override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
    val base = cfg.baseUrl.trim()
    if (base.isBlank() || base.contains("example.invalid")) {
      return TransportResult.PermanentFailure("baseUrl not configured")
    }

    val endpoint = cfg.syncPolicy.endpointPath.trim().trimStart('/')
    val url = ensureSlash(base) + (if (endpoint.isBlank()) "events/batch" else endpoint)

    return try {
      val payload = json.encodeToString(request)
      val body = payload.toRequestBody(contentType)

      val rb = Request.Builder()
        .url(url)
        .post(body)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("X-KioskOps-SDK", com.peterz.kioskops.sdk.KioskOpsSdk.SDK_VERSION)

      authProvider?.apply(rb)

      val resp = client.newCall(rb.build()).execute()
      val code = resp.code
      val bodyStr = resp.body?.string().orEmpty()

      if (code in 200..299) {
        val parsed = json.decodeFromString<BatchSendResponse>(bodyStr)
        TransportResult.Success(parsed, httpStatus = code)
      } else {
        val msg = "HTTP $code ${bodyStr.take(300)}"
        logs.w("Transport", msg)
        // 429 & 5xx => transient; 401/403 often resolve after token refresh => transient
        if (code == 429 || code >= 500 || code == 401 || code == 403) {
          TransportResult.TransientFailure(message = msg, httpStatus = code)
        } else {
          TransportResult.PermanentFailure(message = msg, httpStatus = code)
        }
      }
    } catch (t: Throwable) {
      logs.w("Transport", "Network/serialization error", t)
      TransportResult.TransientFailure("exception: ${t.javaClass.simpleName}", cause = t)
    }
  }

  private fun ensureSlash(s: String): String = if (s.endsWith("/")) s else "$s/"
}
