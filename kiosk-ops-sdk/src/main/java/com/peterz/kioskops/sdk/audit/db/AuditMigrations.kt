/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Audit database migrations.
 *
 * NIST AU-11: Audit records must not be lost during schema evolution.
 * All migrations use ALTER TABLE (non-destructive) to preserve existing data.
 */
object AuditMigrations {

  /** v1 -> v2: Add userId column for GDPR data subject tracking. */
  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE audit_events ADD COLUMN userId TEXT")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_userId ON audit_events(userId)")
    }
  }
}
