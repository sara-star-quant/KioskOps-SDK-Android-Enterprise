/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.validation

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for event type schemas.
 *
 * Schemas are stored as raw JSON strings conforming to a Draft 2020-12 subset.
 *
 * @since 0.5.0
 */
class SchemaRegistry {
  private val schemas = ConcurrentHashMap<String, String>()

  /**
   * Register a JSON schema for an event type.
   *
   * @param eventType Event type identifier.
   * @param schemaJson JSON Schema string.
   */
  fun register(eventType: String, schemaJson: String) {
    schemas[eventType] = schemaJson
  }

  /**
   * Get the schema for an event type.
   *
   * @param eventType Event type identifier.
   * @return Schema JSON or null if not registered.
   */
  fun getSchema(eventType: String): String? = schemas[eventType]

  /**
   * List all registered event types.
   */
  fun listRegistered(): Set<String> = schemas.keys.toSet()

  /**
   * Remove a schema registration.
   */
  fun unregister(eventType: String) {
    schemas.remove(eventType)
  }

  /**
   * Remove all registered schemas.
   */
  fun clear() {
    schemas.clear()
  }
}
