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
   * v2 -> v3: Add monotonic seq and formatVersion to events, prune watermark to chain state.
   * Existing rows get seq in (ts, id) order, the order the v1 hash format verified in.
   */
  val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE audit_events ADD COLUMN seq INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE audit_events ADD COLUMN formatVersion INTEGER NOT NULL DEFAULT 1")
      db.execSQL(
        """UPDATE audit_events SET seq = (
             SELECT COUNT(*) FROM audit_events b
             WHERE b.ts < audit_events.ts OR (b.ts = audit_events.ts AND b.id <= audit_events.id)
           )"""
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_seq ON audit_events(seq)")
      db.execSQL("ALTER TABLE audit_chain_state ADD COLUMN prunedThroughSeq INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE audit_chain_state ADD COLUMN prunedThroughHash TEXT NOT NULL DEFAULT 'GENESIS'")
    }
  }
}
