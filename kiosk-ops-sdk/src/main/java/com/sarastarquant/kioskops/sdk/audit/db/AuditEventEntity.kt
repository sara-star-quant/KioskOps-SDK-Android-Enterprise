/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.db

import androidx.annotation.RestrictTo
import androidx.room.ColumnInfo
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
 * @property seq Strictly increasing position (not necessarily gap-free) in the chain, starting at 1. 0 only on unsaved legacy values.
 * @property formatVersion Hash format version used to compute [hash].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@Entity(
  tableName = "audit_events",
  indices = [
    Index(value = ["ts"]),
    Index(value = ["name"]),
    Index(value = ["chainGeneration", "ts"]),
    Index(value = ["userId"]),
    Index(value = ["seq"]),
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
  /** @since 0.5.0 */
  val userId: String? = null,
  @ColumnInfo(defaultValue = "0")
  val seq: Long = 0,
  @ColumnInfo(defaultValue = "1")
  val formatVersion: Int = 1,
) {
  @Suppress("CyclomaticComplexMethod")
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
      chainGeneration == other.chainGeneration &&
      userId == other.userId &&
      seq == other.seq &&
      formatVersion == other.formatVersion
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
    result = 31 * result + (userId?.hashCode() ?: 0)
    result = 31 * result + seq.hashCode()
    result = 31 * result + formatVersion
    return result
  }
}

/**
 * Tracks the current state of the audit chain.
 *
 * This is stored separately to enable atomic updates and
 * quick access to the last hash for chaining.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@Entity(tableName = "audit_chain_state")
data class AuditChainState(
  @PrimaryKey
  val id: Int = 1,
  val lastHash: String,
  val lastTs: Long,
  val chainGeneration: Int,
  val eventCount: Long,
  @ColumnInfo(defaultValue = "0")
  val prunedThroughSeq: Long = 0,
  @ColumnInfo(defaultValue = "'GENESIS'")
  val prunedThroughHash: String = "GENESIS",
  /** headSeq of the last checkpoint a server acknowledged; 0 before the first ack. */
  @ColumnInfo(defaultValue = "0")
  val lastAckedSeq: Long = 0,
)
