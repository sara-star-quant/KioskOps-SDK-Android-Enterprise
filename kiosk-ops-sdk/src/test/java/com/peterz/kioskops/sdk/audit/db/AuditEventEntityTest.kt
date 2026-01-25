/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditEventEntityTest {

  @Test
  fun `entity contains all required fields`() {
    val entity = AuditEventEntity(
      id = "event-123",
      ts = 1000L,
      name = "test_event",
      fieldsJson = """{"key":"value"}""",
      prevHash = "prev-hash-abc",
      hash = "hash-def",
      signature = null,
      deviceAttestationBlob = null,
      chainGeneration = 1,
    )

    assertThat(entity.id).isEqualTo("event-123")
    assertThat(entity.ts).isEqualTo(1000L)
    assertThat(entity.name).isEqualTo("test_event")
    assertThat(entity.fieldsJson).isEqualTo("""{"key":"value"}""")
    assertThat(entity.prevHash).isEqualTo("prev-hash-abc")
    assertThat(entity.hash).isEqualTo("hash-def")
    assertThat(entity.signature).isNull()
    assertThat(entity.deviceAttestationBlob).isNull()
    assertThat(entity.chainGeneration).isEqualTo(1)
  }

  @Test
  fun `entity with signature and attestation blob`() {
    val blob = ByteArray(100) { it.toByte() }
    val entity = AuditEventEntity(
      id = "event-456",
      ts = 2000L,
      name = "signed_event",
      fieldsJson = "{}",
      prevHash = "prev",
      hash = "curr",
      signature = "base64-signature",
      deviceAttestationBlob = blob,
      chainGeneration = 2,
    )

    assertThat(entity.signature).isEqualTo("base64-signature")
    assertThat(entity.deviceAttestationBlob).isEqualTo(blob)
    assertThat(entity.chainGeneration).isEqualTo(2)
  }

  @Test
  fun `entity equals with same values`() {
    val entity1 = AuditEventEntity(
      id = "event-1",
      ts = 1000L,
      name = "test",
      fieldsJson = "{}",
      prevHash = "prev",
      hash = "curr",
      chainGeneration = 1,
    )
    val entity2 = AuditEventEntity(
      id = "event-1",
      ts = 1000L,
      name = "test",
      fieldsJson = "{}",
      prevHash = "prev",
      hash = "curr",
      chainGeneration = 1,
    )

    assertThat(entity1).isEqualTo(entity2)
    assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode())
  }

  @Test
  fun `entity not equal with different id`() {
    val entity1 = AuditEventEntity(
      id = "event-1",
      ts = 1000L,
      name = "test",
      fieldsJson = "{}",
      prevHash = "prev",
      hash = "curr",
      chainGeneration = 1,
    )
    val entity2 = entity1.copy(id = "event-2")

    assertThat(entity1).isNotEqualTo(entity2)
  }

  @Test
  fun `entity equals with attestation blob`() {
    val blob = ByteArray(10) { 1 }
    val entity1 = AuditEventEntity(
      id = "event-1",
      ts = 1000L,
      name = "test",
      fieldsJson = "{}",
      prevHash = "prev",
      hash = "curr",
      deviceAttestationBlob = blob,
      chainGeneration = 1,
    )
    val entity2 = AuditEventEntity(
      id = "event-1",
      ts = 1000L,
      name = "test",
      fieldsJson = "{}",
      prevHash = "prev",
      hash = "curr",
      deviceAttestationBlob = blob.copyOf(),
      chainGeneration = 1,
    )

    assertThat(entity1).isEqualTo(entity2)
  }

  @Test
  fun `AuditChainState contains all fields`() {
    val state = AuditChainState(
      id = 1,
      lastHash = "last-hash",
      lastTs = 5000L,
      chainGeneration = 3,
      eventCount = 100,
    )

    assertThat(state.id).isEqualTo(1)
    assertThat(state.lastHash).isEqualTo("last-hash")
    assertThat(state.lastTs).isEqualTo(5000L)
    assertThat(state.chainGeneration).isEqualTo(3)
    assertThat(state.eventCount).isEqualTo(100)
  }
}
