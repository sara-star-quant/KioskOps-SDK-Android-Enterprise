/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Base64

/**
 * Encrypts individual JSON fields into envelope format before document encryption.
 *
 * Encrypted fields are replaced with:
 * ```json
 * {"__enc":"<base64>","__alg":"AES-256-GCM","__kid":"v1"}
 * ```
 *
 * Uses the existing [CryptoProvider] infrastructure.
 * NIST SC-28: Protection of Information at Rest.
 *
 * @since 0.5.0
 */
class FieldLevelEncryptor(
  private val crypto: CryptoProvider,
) {

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Encrypt specified fields in a JSON payload.
   *
   * @param payloadJson The JSON payload.
   * @param fieldsToEncrypt Set of top-level field names to encrypt.
   * @return JSON string with specified fields encrypted.
   */
  fun encryptFields(payloadJson: String, fieldsToEncrypt: Set<String>): String {
    if (fieldsToEncrypt.isEmpty() || !crypto.isEnabled) return payloadJson

    return try {
      val element = json.parseToJsonElement(payloadJson)
      if (element !is JsonObject) return payloadJson

      val result = buildJsonObject {
        for ((key, value) in element) {
          if (key in fieldsToEncrypt && value is JsonPrimitive) {
            val plainBytes = value.toString().toByteArray(Charsets.UTF_8)
            val encrypted = crypto.encrypt(plainBytes)
            val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
            put(key, buildJsonObject {
              put("__enc", JsonPrimitive(b64))
              put("__alg", JsonPrimitive("AES-256-GCM"))
              put("__kid", JsonPrimitive("v1"))
            })
          } else {
            put(key, value)
          }
        }
      }
      result.toString()
    } catch (_: Exception) {
      // Fail-safe: return original payload if encryption fails
      payloadJson
    }
  }

  /**
   * Decrypt specified fields in a JSON payload.
   *
   * @param payloadJson The JSON payload with encrypted field envelopes.
   * @return JSON string with decrypted fields.
   */
  fun decryptFields(payloadJson: String): String {
    if (!crypto.isEnabled) return payloadJson

    return try {
      val element = json.parseToJsonElement(payloadJson)
      if (element !is JsonObject) return payloadJson

      val result = buildJsonObject {
        for ((key, value) in element) {
          if (isEncryptedEnvelope(value)) {
            val obj = value as JsonObject
            val b64 = (obj["__enc"] as? JsonPrimitive)?.content ?: ""
            val encrypted = Base64.getUrlDecoder().decode(b64)
            val decrypted = crypto.decrypt(encrypted)
            val decryptedStr = String(decrypted, Charsets.UTF_8)
            // Parse back to JsonElement to preserve type
            put(key, json.parseToJsonElement(decryptedStr))
          } else {
            put(key, value)
          }
        }
      }
      result.toString()
    } catch (_: Exception) {
      payloadJson
    }
  }

  private fun isEncryptedEnvelope(element: JsonElement): Boolean {
    if (element !is JsonObject) return false
    return element.containsKey("__enc") && element.containsKey("__alg")
  }
}
