package com.peterz.kioskops.sdk.fleet

import java.io.File

/**
 * Host-controlled upload hook.
 *
 * The SDK never uploads data by itself: you must provide an implementation.
 * This makes data residency + consent/legal basis explicit and auditable.
 */
fun interface DiagnosticsUploader {
  suspend fun upload(file: File, metadata: Map<String, String>)
}

suspend fun DiagnosticsUploader.upload(file: File) = upload(file, emptyMap())
