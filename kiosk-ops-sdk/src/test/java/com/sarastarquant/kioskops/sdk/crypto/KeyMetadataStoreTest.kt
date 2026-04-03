/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [KeyMetadataStore].
 *
 * Verifies metadata persistence, version tracking, creation-time recording,
 * initialization idempotency, and key-age calculation using Robolectric
 * SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
class KeyMetadataStoreTest {

  private lateinit var ctx: Context

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
  }

  private fun createStore(alias: String = "test_alias"): KeyMetadataStore {
    return KeyMetadataStore(ctx, alias)
  }

  // -------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------

  @Test
  fun `getCurrentVersion returns 1 on fresh store`() {
    val store = createStore("fresh")
    assertThat(store.getCurrentVersion()).isEqualTo(1)
  }

  @Test
  fun `getKeyMetadata returns null for non-existent version`() {
    val store = createStore("empty")
    assertThat(store.getKeyMetadata(1)).isNull()
    assertThat(store.getKeyMetadata(99)).isNull()
  }

  @Test
  fun `getAllVersions returns empty list on fresh store`() {
    val store = createStore("no_versions")
    assertThat(store.getAllVersions()).isEmpty()
  }

  // -------------------------------------------------------------------------
  // setKeyMetadata and getKeyMetadata
  // -------------------------------------------------------------------------

  @Test
  fun `setKeyMetadata then getKeyMetadata round-trips correctly`() {
    val store = createStore("roundtrip")
    val metadata = KeyMetadata(
      version = 1,
      createdAtMs = 1_700_000_000_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
    )

    store.setKeyMetadata(1, metadata)
    val retrieved = store.getKeyMetadata(1)

    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.version).isEqualTo(1)
    assertThat(retrieved.createdAtMs).isEqualTo(1_700_000_000_000L)
    assertThat(retrieved.algorithm).isEqualTo("AES/GCM/NoPadding")
    assertThat(retrieved.keyLengthBits).isEqualTo(256)
    assertThat(retrieved.rotatedFromVersion).isNull()
    assertThat(retrieved.isHardwareBacked).isNull()
  }

  @Test
  fun `setKeyMetadata preserves optional fields`() {
    val store = createStore("optional")
    val metadata = KeyMetadata(
      version = 2,
      createdAtMs = 1_700_000_000_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
      rotatedFromVersion = 1,
      isHardwareBacked = true,
    )

    store.setKeyMetadata(2, metadata)
    val retrieved = store.getKeyMetadata(2)

    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.rotatedFromVersion).isEqualTo(1)
    assertThat(retrieved.isHardwareBacked).isTrue()
  }

  @Test
  fun `setKeyMetadata overwrites existing metadata`() {
    val store = createStore("overwrite")
    val original = KeyMetadata(
      version = 1,
      createdAtMs = 1_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 128,
    )
    val updated = KeyMetadata(
      version = 1,
      createdAtMs = 2_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
    )

    store.setKeyMetadata(1, original)
    store.setKeyMetadata(1, updated)
    val retrieved = store.getKeyMetadata(1)

    assertThat(retrieved!!.createdAtMs).isEqualTo(2_000L)
    assertThat(retrieved.keyLengthBits).isEqualTo(256)
  }

  // -------------------------------------------------------------------------
  // setCurrentVersion and getCurrentVersion
  // -------------------------------------------------------------------------

  @Test
  fun `setCurrentVersion updates the current version`() {
    val store = createStore("set_version")

    store.setCurrentVersion(3)
    assertThat(store.getCurrentVersion()).isEqualTo(3)

    store.setCurrentVersion(5)
    assertThat(store.getCurrentVersion()).isEqualTo(5)
  }

  // -------------------------------------------------------------------------
  // getAllVersions
  // -------------------------------------------------------------------------

  @Test
  fun `getAllVersions returns stored versions in sorted order`() {
    val store = createStore("all_versions")
    val base = KeyMetadata(
      version = 0,
      createdAtMs = 1_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
    )

    store.setKeyMetadata(3, base.copy(version = 3))
    store.setKeyMetadata(1, base.copy(version = 1))
    store.setKeyMetadata(2, base.copy(version = 2))

    val versions = store.getAllVersions()
    assertThat(versions).isEqualTo(listOf(1, 2, 3))
  }

  // -------------------------------------------------------------------------
  // deleteKeyMetadata
  // -------------------------------------------------------------------------

  @Test
  fun `deleteKeyMetadata removes metadata for a version`() {
    val store = createStore("delete")
    val metadata = KeyMetadata(
      version = 1,
      createdAtMs = 1_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
    )

    store.setKeyMetadata(1, metadata)
    assertThat(store.getKeyMetadata(1)).isNotNull()

    store.deleteKeyMetadata(1)
    assertThat(store.getKeyMetadata(1)).isNull()
  }

  @Test
  fun `deleteKeyMetadata for non-existent version is a no-op`() {
    val store = createStore("delete_noop")
    // Should not throw
    store.deleteKeyMetadata(99)
    assertThat(store.getKeyMetadata(99)).isNull()
  }

  @Test
  fun `deleteKeyMetadata does not affect other versions`() {
    val store = createStore("delete_selective")
    val base = KeyMetadata(
      version = 0,
      createdAtMs = 1_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
    )

    store.setKeyMetadata(1, base.copy(version = 1))
    store.setKeyMetadata(2, base.copy(version = 2))

    store.deleteKeyMetadata(1)

    assertThat(store.getKeyMetadata(1)).isNull()
    assertThat(store.getKeyMetadata(2)).isNotNull()
  }

  // -------------------------------------------------------------------------
  // initializeIfNeeded
  // -------------------------------------------------------------------------

  @Test
  fun `initializeIfNeeded creates version 1 metadata on first call`() {
    val store = createStore("init_first")
    val now = 1_700_000_000_000L

    store.initializeIfNeeded(now)

    val metadata = store.getKeyMetadata(1)
    assertThat(metadata).isNotNull()
    assertThat(metadata!!.version).isEqualTo(1)
    assertThat(metadata.createdAtMs).isEqualTo(now)
    assertThat(metadata.algorithm).isEqualTo("AES/GCM/NoPadding")
    assertThat(metadata.keyLengthBits).isEqualTo(256)
    assertThat(store.getCurrentVersion()).isEqualTo(1)
  }

  @Test
  fun `initializeIfNeeded is idempotent and preserves original timestamp`() {
    val store = createStore("init_idempotent")

    store.initializeIfNeeded(1_000_000L)
    store.initializeIfNeeded(2_000_000L)

    val metadata = store.getKeyMetadata(1)
    assertThat(metadata).isNotNull()
    // Original timestamp is preserved
    assertThat(metadata!!.createdAtMs).isEqualTo(1_000_000L)
  }

  @Test
  fun `initializeIfNeeded does not overwrite manually set version 1`() {
    val store = createStore("init_no_overwrite")
    val manualMeta = KeyMetadata(
      version = 1,
      createdAtMs = 999L,
      algorithm = "AES/CBC/PKCS5Padding",
      keyLengthBits = 128,
    )

    store.setKeyMetadata(1, manualMeta)
    store.initializeIfNeeded(5_000L)

    val metadata = store.getKeyMetadata(1)
    assertThat(metadata!!.createdAtMs).isEqualTo(999L)
    assertThat(metadata.algorithm).isEqualTo("AES/CBC/PKCS5Padding")
  }

  // -------------------------------------------------------------------------
  // getCurrentKeyMetadata
  // -------------------------------------------------------------------------

  @Test
  fun `getCurrentKeyMetadata returns metadata for the current version`() {
    val store = createStore("current_meta")
    val v1 = KeyMetadata(
      version = 1,
      createdAtMs = 1_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
    )
    val v2 = KeyMetadata(
      version = 2,
      createdAtMs = 2_000L,
      algorithm = "AES/GCM/NoPadding",
      keyLengthBits = 256,
      rotatedFromVersion = 1,
    )

    store.setKeyMetadata(1, v1)
    store.setKeyMetadata(2, v2)
    store.setCurrentVersion(2)

    val current = store.getCurrentKeyMetadata()
    assertThat(current).isNotNull()
    assertThat(current!!.version).isEqualTo(2)
    assertThat(current.rotatedFromVersion).isEqualTo(1)
  }

  @Test
  fun `getCurrentKeyMetadata returns null when no metadata stored for current version`() {
    val store = createStore("current_null")
    store.setCurrentVersion(5)
    assertThat(store.getCurrentKeyMetadata()).isNull()
  }

  // -------------------------------------------------------------------------
  // getCurrentKeyAgeDays
  // -------------------------------------------------------------------------

  @Test
  fun `getCurrentKeyAgeDays returns 0 for freshly created key`() {
    val store = createStore("age_fresh")
    val now = 1_700_000_000_000L
    store.initializeIfNeeded(now)

    val ageDays = store.getCurrentKeyAgeDays(now)
    assertThat(ageDays).isEqualTo(0)
  }

  @Test
  fun `getCurrentKeyAgeDays returns correct age in days`() {
    val store = createStore("age_days")
    val createdAt = 1_700_000_000_000L
    store.initializeIfNeeded(createdAt)

    val oneDayMs = 24L * 60 * 60 * 1000
    val ageDays = store.getCurrentKeyAgeDays(createdAt + 30 * oneDayMs)
    assertThat(ageDays).isEqualTo(30)
  }

  @Test
  fun `getCurrentKeyAgeDays returns null when no metadata exists`() {
    val store = createStore("age_null")
    // No metadata initialized
    val ageDays = store.getCurrentKeyAgeDays(1_700_000_000_000L)
    assertThat(ageDays).isNull()
  }

  @Test
  fun `getCurrentKeyAgeDays truncates partial days`() {
    val store = createStore("age_partial")
    val createdAt = 1_700_000_000_000L
    store.initializeIfNeeded(createdAt)

    // 1.5 days should truncate to 1
    val oneDayMs = 24L * 60 * 60 * 1000
    val ageDays = store.getCurrentKeyAgeDays(createdAt + oneDayMs + oneDayMs / 2)
    assertThat(ageDays).isEqualTo(1)
  }

  // -------------------------------------------------------------------------
  // Isolation between different base aliases
  // -------------------------------------------------------------------------

  @Test
  fun `stores with different aliases are independent`() {
    val storeA = createStore("alias_a")
    val storeB = createStore("alias_b")

    storeA.initializeIfNeeded(1_000L)
    storeB.initializeIfNeeded(2_000L)

    assertThat(storeA.getKeyMetadata(1)!!.createdAtMs).isEqualTo(1_000L)
    assertThat(storeB.getKeyMetadata(1)!!.createdAtMs).isEqualTo(2_000L)
  }

  // -------------------------------------------------------------------------
  // Corrupt JSON handling
  // -------------------------------------------------------------------------

  @Test
  fun `getKeyMetadata returns null for corrupt JSON`() {
    val store = createStore("corrupt")
    // Manually write corrupt data via SharedPreferences
    val prefs = ctx.getSharedPreferences("kioskops_key_metadata_corrupt", Context.MODE_PRIVATE)
    prefs.edit().putString("key_meta_v1", "not valid json{{{").apply()

    assertThat(store.getKeyMetadata(1)).isNull()
  }
}
