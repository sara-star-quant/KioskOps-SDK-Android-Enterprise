/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for persistent audit events.
 *
 * Each event is part of a hash chain, where [hash] includes the [prevHash]
 * to create a tamper-evident log structure.
 *
 * @property id Unique event identifier (UUID).
 * @property ts Event timestamp in milliseconds since epoch.
 * @property name Event name (e.g., "sdk_initialized", "event_enqueued").
 * @property fieldsJson JSON-serialized map of event fields.
 * @property prevHash Hash of the previous event in the chain.
 * @property hash SHA-256 hash of this event (includes prevHash).
 * @property signature Optional ECDSA signature from device attestation key.
 * @property deviceAttestationBlob Optional attestation certificate chain.
 * @property chainGeneration Chain generation number (increments on restart if file-based).
 */
@Entity(
  tableName = "audit_events",
  indices = [
    Index(value = ["ts"]),
    Index(value = ["name"]),
    Index(value = ["chainGeneration", "ts"]),
  ]
)
data class AuditEventEntity(
  @PrimaryKey
  val id: String,
  val ts: Long,
  val name: String,
  val fieldsJson: String,
  val prevHash: String,
  val hash: String,
  val signature: String? = null,
  val deviceAttestationBlob: ByteArray? = null,
  val chainGeneration: Int = 1,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AuditEventEntity) return false
    return id == other.id &&
      ts == other.ts &&
      name == other.name &&
      fieldsJson == other.fieldsJson &&
      prevHash == other.prevHash &&
      hash == other.hash &&
      signature == other.signature &&
      (deviceAttestationBlob?.contentEquals(other.deviceAttestationBlob) ?: (other.deviceAttestationBlob == null)) &&
      chainGeneration == other.chainGeneration
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + ts.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + fieldsJson.hashCode()
    result = 31 * result + prevHash.hashCode()
    result = 31 * result + hash.hashCode()
    result = 31 * result + (signature?.hashCode() ?: 0)
    result = 31 * result + (deviceAttestationBlob?.contentHashCode() ?: 0)
    result = 31 * result + chainGeneration
    return result
  }
}

/**
 * Tracks the current state of the audit chain.
 *
 * This is stored separately to enable atomic updates and
 * quick access to the last hash for chaining.
 */
@Entity(tableName = "audit_chain_state")
data class AuditChainState(
  @PrimaryKey
  val id: Int = 1,
  val lastHash: String,
  val lastTs: Long,
  val chainGeneration: Int,
  val eventCount: Long,
)
