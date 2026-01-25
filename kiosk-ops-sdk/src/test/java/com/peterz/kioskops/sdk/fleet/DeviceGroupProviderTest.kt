/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for DeviceGroupProvider and DefaultDeviceGroupProvider.
 *
 * Security (ISO 27001 A.8): Validates fleet segmentation for asset management.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceGroupProviderTest {

  private lateinit var context: Context
  private lateinit var provider: DefaultDeviceGroupProvider

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    // Clear preferences from previous tests
    context.getSharedPreferences("kioskops_device_groups", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .apply()

    provider = DefaultDeviceGroupProvider(context)
  }

  @Test
  fun `initially returns empty list`() {
    assertThat(provider.getDeviceGroups()).isEmpty()
  }

  @Test
  fun `addToGroup adds group`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      assertThat(provider.getDeviceGroups()).containsExactly("retail-stores")
    }
  }

  @Test
  fun `addToGroup is idempotent`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.addToGroup("retail-stores")
      assertThat(provider.getDeviceGroups()).containsExactly("retail-stores")
    }
  }

  @Test
  fun `addToGroup adds multiple groups`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.addToGroup("west-coast")
      provider.addToGroup("beta-testing")
      // Groups are returned sorted alphabetically
      assertThat(provider.getDeviceGroups()).containsExactly(
        "beta-testing",
        "retail-stores",
        "west-coast",
      ).inOrder()
    }
  }

  @Test
  fun `removeFromGroup removes group`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.addToGroup("west-coast")

      provider.removeFromGroup("retail-stores")

      assertThat(provider.getDeviceGroups()).containsExactly("west-coast")
    }
  }

  @Test
  fun `removeFromGroup is idempotent`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.removeFromGroup("retail-stores")
      provider.removeFromGroup("retail-stores")
      assertThat(provider.getDeviceGroups()).isEmpty()
    }
  }

  @Test
  fun `removeFromGroup handles non-existent group`() {
    runBlocking {
      provider.removeFromGroup("non-existent")
      assertThat(provider.getDeviceGroups()).isEmpty()
    }
  }

  @Test
  fun `setGroups replaces all groups`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.addToGroup("west-coast")

      provider.setGroups(listOf("new-group", "another-group"))

      // Groups are sorted
      assertThat(provider.getDeviceGroups()).containsExactly(
        "another-group",
        "new-group",
      ).inOrder()
    }
  }

  @Test
  fun `setGroups with empty list clears groups`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.setGroups(emptyList())
      assertThat(provider.getDeviceGroups()).isEmpty()
    }
  }

  @Test
  fun `groups persist across instances`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.addToGroup("west-coast")

      // Create new instance
      val newProvider = DefaultDeviceGroupProvider(context)

      assertThat(newProvider.getDeviceGroups()).containsExactly(
        "retail-stores",
        "west-coast",
      ).inOrder()
    }
  }

  @Test
  fun `isInGroup returns true for existing group`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      assertThat(provider.isInGroup("retail-stores")).isTrue()
    }
  }

  @Test
  fun `isInGroup returns false for non-existent group`() {
    assertThat(provider.isInGroup("non-existent")).isFalse()
  }

  @Test
  fun `special characters in group names preserved`() {
    runBlocking {
      val specialGroups = listOf("us-east-1", "tier_1", "device.category.retail")
      provider.setGroups(specialGroups)
      // Sorted alphabetically
      assertThat(provider.getDeviceGroups()).containsExactly(
        "device.category.retail",
        "tier_1",
        "us-east-1",
      ).inOrder()
    }
  }

  @Test
  fun `unicode group names supported`() {
    runBlocking {
      val unicodeGroups = listOf("gruppe-eins", "グループ二", "组三")
      provider.setGroups(unicodeGroups)
      // Will be sorted by Unicode codepoint
      assertThat(provider.getDeviceGroups()).hasSize(3)
      assertThat(provider.getDeviceGroups()).containsExactlyElementsIn(unicodeGroups)
    }
  }

  @Test
  fun `groups are distinct`() {
    runBlocking {
      provider.addToGroup("retail-stores")
      provider.addToGroup("retail-stores")
      provider.addToGroup("retail-stores")
      assertThat(provider.getDeviceGroups()).hasSize(1)
    }
  }
}
