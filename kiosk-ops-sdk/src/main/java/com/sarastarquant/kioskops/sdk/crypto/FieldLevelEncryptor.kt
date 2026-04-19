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
 * Thrown when field-level encryption or decryption fails. Callers MUST NOT forward
 * the original plaintext when this is raised; doing so defeats the purpose of
 * field-level encryption. Instead, reject the event or surface the failure to an
 * error listener.
 *
 * @since 1.1.0
 */
class FieldEncryptionException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

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
 * @since 1.1.0 Throws [FieldEncryptionException] on failure instead of silently
 *   falling back to plaintext (previous behavior leaked PII when the Keystore key
 *   was unavailable).
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
   * @throws FieldEncryptionException if any requested field cannot be encrypted.
   */
  fun encryptFields(payloadJson: String, fieldsToEncrypt: Set<String>): String {
    if (fieldsToEncrypt.isEmpty() || !crypto.isEnabled) return payloadJson

    val element = parseJsonOrThrow(payloadJson, "encrypt")
    if (element !is JsonObject) return payloadJson

    @Suppress("TooGenericExceptionCaught")
    return try {
      buildJsonObject {
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
      }.toString()
    } catch (e: Exception) {
      throw FieldEncryptionException(
        "Field-level encryption failed for fields=$fieldsToEncrypt: ${e.message}",
        e,
      )
    }
  }

  /**
   * Decrypt specified fields in a JSON payload.
   *
   * @param payloadJson The JSON payload with encrypted field envelopes.
   * @return JSON string with decrypted fields.
   * @throws FieldEncryptionException if any encrypted envelope cannot be decrypted.
   */
  fun decryptFields(payloadJson: String): String {
    if (!crypto.isEnabled) return payloadJson

    val element = parseJsonOrThrow(payloadJson, "decrypt")
    if (element !is JsonObject) return payloadJson

    @Suppress("TooGenericExceptionCaught")
    return try {
      buildJsonObject {
        for ((key, value) in element) {
          if (isEncryptedEnvelope(value)) {
            val obj = value as JsonObject
            val b64 = (obj["__enc"] as? JsonPrimitive)?.content ?: ""
            val encrypted = Base64.getUrlDecoder().decode(b64)
            val decrypted = crypto.decrypt(encrypted)
            val decryptedStr = String(decrypted, Charsets.UTF_8)
            put(key, json.parseToJsonElement(decryptedStr))
          } else {
            put(key, value)
          }
        }
      }.toString()
    } catch (e: Exception) {
      throw FieldEncryptionException(
        "Field-level decryption failed: ${e.message}",
        e,
      )
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun parseJsonOrThrow(payload: String, op: String): JsonElement {
    return try {
      json.parseToJsonElement(payload)
    } catch (e: Exception) {
      throw FieldEncryptionException("Malformed JSON passed to field-level $op", e)
    }
  }

  private fun isEncryptedEnvelope(element: JsonElement): Boolean {
    if (element !is JsonObject) return false
    return element.containsKey("__enc") && element.containsKey("__alg")
  }
}
