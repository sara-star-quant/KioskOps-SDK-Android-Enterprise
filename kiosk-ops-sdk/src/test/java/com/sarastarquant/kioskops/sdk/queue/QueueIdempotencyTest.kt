/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueIdempotencyTest : QueueTestBase() {

  @Test
  fun `duplicate idempotencyKeyOverride is rejected as DuplicateIdempotency`() = runTest {
    val repo = newRepo()

    val first = repo.enqueue("T", "{\"x\":1}", baseCfg, idempotencyKeyOverride = "dup-key")
    assertThat(first.isAccepted).isTrue()

    val second = repo.enqueue("T", "{\"x\":2}", baseCfg, idempotencyKeyOverride = "dup-key")
    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }

  @Test
  fun `same stableEventId produces same idempotency key (deterministic)`() = runTest {
    val repo = newRepo()

    val r1 = repo.enqueue("T", "{\"x\":1}", baseCfg, stableEventId = "stable-1")
    assertThat(r1.isAccepted).isTrue()

    val r2 = repo.enqueue("T", "{\"x\":2}", baseCfg, stableEventId = "stable-1")
    assertThat(r2.isAccepted).isFalse()
    assertThat(r2).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }

  @Test
  fun `different stableEventIds produce different idempotency keys`() = runTest {
    val repo = newRepo()

    val r1 = repo.enqueue("T", "{\"x\":1}", baseCfg, stableEventId = "stable-A")
    val r2 = repo.enqueue("T", "{\"x\":2}", baseCfg, stableEventId = "stable-B")

    assertThat(r1.isAccepted).isTrue()
    assertThat(r2.isAccepted).isTrue()
    val key1 = (r1 as EnqueueResult.Accepted).idempotencyKey
    val key2 = (r2 as EnqueueResult.Accepted).idempotencyKey
    assertThat(key1).isNotEqualTo(key2)
  }

  @Test
  fun `explicit idempotencyKeyOverride is used verbatim`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue(
      "T", "{\"x\":1}", baseCfg,
      idempotencyKeyOverride = "my-exact-key-123",
    )

    assertThat(result.isAccepted).isTrue()
    val accepted = result as EnqueueResult.Accepted
    assertThat(accepted.idempotencyKey).isEqualTo("my-exact-key-123")
  }

  @Test
  fun `without stableEventId or override each enqueue gets a unique key`() = runTest {
    val repo = newRepo()

    val r1 = repo.enqueue("T", "{\"x\":1}", baseCfg)
    val r2 = repo.enqueue("T", "{\"x\":2}", baseCfg)

    assertThat(r1.isAccepted).isTrue()
    assertThat(r2.isAccepted).isTrue()
    val key1 = (r1 as EnqueueResult.Accepted).idempotencyKey
    val key2 = (r2 as EnqueueResult.Accepted).idempotencyKey
    assertThat(key1).isNotEqualTo(key2)
  }

  @Test
  fun `encrypt and decrypt round-trip with NoopCryptoProvider`() = runTest {
    val repo = newRepo()
    val payload = "{\"sensor\":\"temp\",\"value\":42}"

    val result = repo.enqueue("T", payload, baseCfg)
    assertThat(result.isAccepted).isTrue()
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    val decoded = repo.decodePayloadJson(event)
    assertThat(decoded).isEqualTo(payload)
  }

  @Test
  fun `stored event is retrievable in nextBatch`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"k\":\"v\"}", baseCfg)
    assertThat(result.isAccepted).isTrue()
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    assertThat(batch).isNotEmpty()
    assertThat(batch.map { it.id }).contains(id)
  }
}
