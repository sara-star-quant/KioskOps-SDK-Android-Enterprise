/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.db

import androidx.annotation.RestrictTo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Audit database migrations.
 *
 * NIST AU-11: Audit records must not be lost during schema evolution.
 * All migrations use ALTER TABLE (non-destructive) to preserve existing data.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object AuditMigrations {

  /** v1 -> v2: Add userId column for GDPR data subject tracking. */
  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE audit_events ADD COLUMN userId TEXT")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_userId ON audit_events(userId)")
    }
  }

  /**
   * v2 -> v3: Add monotonic seq and formatVersion to events, prune watermark and last acknowledged
   * checkpoint seq to chain state.
   * Existing rows get seq in (ts, id) order, the order the v1 hash format verified in.
   *
   * A chain whose head retention already pruned (first row's prevHash is not GENESIS) gets a prune
   * baseline at that row: trust on first use. Truncation before the upgrade is not detectable;
   * every later one is.
   */
  val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE audit_events ADD COLUMN seq INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE audit_events ADD COLUMN formatVersion INTEGER NOT NULL DEFAULT 1")
      // Single pass: rank once into a keyed temp table, then PK-lookup per row (linear, not quadratic).
      db.execSQL("CREATE TEMP TABLE audit_seq_backfill (id TEXT PRIMARY KEY NOT NULL, rn INTEGER NOT NULL)")
      db.execSQL(
        "INSERT INTO audit_seq_backfill SELECT id, ROW_NUMBER() OVER (ORDER BY ts ASC, id ASC) FROM audit_events"
      )
      db.execSQL("UPDATE audit_events SET seq = (SELECT rn FROM audit_seq_backfill WHERE id = audit_events.id)")
      db.execSQL("DROP TABLE audit_seq_backfill")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_seq ON audit_events(seq)")
      db.execSQL("ALTER TABLE audit_chain_state ADD COLUMN prunedThroughSeq INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE audit_chain_state ADD COLUMN prunedThroughHash TEXT NOT NULL DEFAULT 'GENESIS'")
      db.execSQL("ALTER TABLE audit_chain_state ADD COLUMN lastAckedSeq INTEGER NOT NULL DEFAULT 0")
      db.execSQL(
        "UPDATE audit_chain_state SET " +
          "prunedThroughSeq = (SELECT seq - 1 FROM audit_events ORDER BY seq ASC LIMIT 1), " +
          "prunedThroughHash = (SELECT prevHash FROM audit_events ORDER BY seq ASC LIMIT 1) " +
          "WHERE (SELECT prevHash FROM audit_events ORDER BY seq ASC LIMIT 1) <> 'GENESIS'"
      )
    }
  }
}
