/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.OverflowStrategy
import com.sarastarquant.kioskops.sdk.compliance.QueueLimits
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueuePressureStrategyTest : QueueTestBase() {

  private fun dropOldestCfg(maxActive: Int) = baseCfg.copy(
    queueLimits = QueueLimits(
      maxActiveEvents = maxActive,
      maxActiveBytes = 1024 * 1024,
      overflowStrategy = OverflowStrategy.DROP_OLDEST,
    ),
  )

  private fun dropNewestCfg(maxActive: Int) = baseCfg.copy(
    queueLimits = QueueLimits(
      maxActiveEvents = maxActive,
      maxActiveBytes = 1024 * 1024,
      overflowStrategy = OverflowStrategy.DROP_NEWEST,
    ),
  )

  @Test
  fun `enqueue up to maxActiveEvents succeeds with zero drops`() = runTest {
    val cfg = dropOldestCfg(3)
    val repo = newRepo()

    for (i in 1..3) {
      val r = repo.enqueue("T", "{\"i\":$i}", cfg)
      assertThat(r.isAccepted).isTrue()
      val accepted = r as EnqueueResult.Accepted
      assertThat(accepted.droppedOldest).isEqualTo(0)
    }
    assertThat(repo.countActive()).isEqualTo(3)
  }

  @Test
  fun `DROP_OLDEST removes oldest and reports droppedOldest on overflow`() = runTest {
    val cfg = dropOldestCfg(3)
    val repo = newRepo()

    repo.enqueue("T", "{\"i\":1}", cfg)
    repo.enqueue("T", "{\"i\":2}", cfg)
    repo.enqueue("T", "{\"i\":3}", cfg)

    val fourth = repo.enqueue("T", "{\"i\":4}", cfg)
    assertThat(fourth.isAccepted).isTrue()
    val accepted = fourth as EnqueueResult.Accepted
    assertThat(accepted.droppedOldest).isAtLeast(1)
    assertThat(repo.countActive()).isEqualTo(3)
  }

  @Test
  fun `DROP_OLDEST maintains count ceiling across multiple overflows`() = runTest {
    val cfg = dropOldestCfg(2)
    val repo = newRepo()

    for (i in 1..10) {
      val r = repo.enqueue("T", "{\"i\":$i}", cfg)
      assertThat(r.isAccepted).isTrue()
    }
    assertThat(repo.countActive()).isEqualTo(2)
  }

  @Test
  fun `DROP_NEWEST rejects new event when at capacity`() = runTest {
    val cfg = dropNewestCfg(2)
    val repo = newRepo()

    repo.enqueue("T", "{\"i\":1}", cfg)
    repo.enqueue("T", "{\"i\":2}", cfg)

    val third = repo.enqueue("T", "{\"i\":3}", cfg)
    assertThat(third.isAccepted).isFalse()
    assertThat(third).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
    assertThat(repo.countActive()).isEqualTo(2)
  }

  @Test
  fun `DROP_NEWEST preserves existing events unchanged`() = runTest {
    val cfg = dropNewestCfg(2)
    val repo = newRepo()

    val first = repo.enqueue("T", "{\"i\":1}", cfg) as EnqueueResult.Accepted
    val second = repo.enqueue("T", "{\"i\":2}", cfg) as EnqueueResult.Accepted

    repo.enqueue("T", "{\"i\":3}", cfg)

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val ids = batch.map { it.id }.toSet()
    assertThat(ids).contains(first.id)
    assertThat(ids).contains(second.id)
  }

  @Test
  fun `BLOCK strategy rejects new event when at capacity`() = runTest {
    val cfg = baseCfg.copy(
      queueLimits = QueueLimits(
        maxActiveEvents = 1,
        maxActiveBytes = 1024 * 1024,
        overflowStrategy = OverflowStrategy.BLOCK,
      ),
    )
    val repo = newRepo()

    repo.enqueue("T", "{\"i\":1}", cfg)
    val second = repo.enqueue("T", "{\"i\":2}", cfg)

    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
  }

  @Test
  fun `maxActiveBytes triggers overflow even when event count is under limit`() = runTest {
    val cfg = baseCfg.copy(
      queueLimits = QueueLimits(
        maxActiveEvents = 100,
        maxActiveBytes = 10,
        overflowStrategy = OverflowStrategy.DROP_NEWEST,
      ),
    )
    val repo = newRepo()

    val first = repo.enqueue("T", "{\"i\":1}", cfg)
    assertThat(first.isAccepted).isTrue()

    val second = repo.enqueue("T", "{\"i\":2}", cfg)
    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
  }
}
