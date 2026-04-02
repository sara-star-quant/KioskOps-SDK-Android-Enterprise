/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.geofence

import android.Manifest
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Tests for [GeofenceManager].
 *
 * Covers initialization, start/stop monitoring, transition handling,
 * profile switching, listener management, and edge cases.
 *
 * AuditTrail is passed as null to avoid needing CryptoProvider / file I/O.
 */
@RunWith(RobolectricTestRunner::class)
class GeofenceManagerTest {

  private lateinit var context: Context
  private val fixedClock: Clock = Clock.fixed(
    Instant.parse("2026-01-15T12:00:00Z"),
    ZoneId.of("UTC"),
  )

  private val regionA = GeofenceRegion(
    id = "region-a",
    latitude = 40.7128,
    longitude = -74.0060,
    radiusMeters = 200f,
    policyProfile = "store-floor",
    priority = 10,
  )

  private val regionB = GeofenceRegion(
    id = "region-b",
    latitude = 34.0522,
    longitude = -118.2437,
    radiusMeters = 500f,
    policyProfile = "warehouse",
    priority = 5,
  )

  private val regionC = GeofenceRegion(
    id = "region-c",
    latitude = 51.5074,
    longitude = -0.1278,
    radiusMeters = 100f,
    policyProfile = "secure-zone",
    priority = 20,
  )

  private val profiles = mapOf(
    PolicyProfile.DEFAULT_PROFILE_NAME to PolicyProfile(name = PolicyProfile.DEFAULT_PROFILE_NAME),
    "store-floor" to PolicyProfile(name = "store-floor", description = "Store floor profile"),
    "warehouse" to PolicyProfile(name = "warehouse", description = "Warehouse profile"),
    "secure-zone" to PolicyProfile(name = "secure-zone", description = "Secure zone profile"),
  )

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  // ---------------------------------------------------------------------------
  // Helper: build a GeofenceManager with specified policy
  // ---------------------------------------------------------------------------

  private fun buildManager(
    policy: GeofencePolicy = GeofencePolicy(
      enabled = true,
      regions = listOf(regionA, regionB),
    ),
  ): GeofenceManager {
    return GeofenceManager(
      context = context,
      policyProvider = { policy },
      profileProvider = { name -> profiles[name] },
      auditTrail = null,
      clock = fixedClock,
    )
  }

  private fun grantLocationPermission() {
    val app = shadowOf(context.applicationContext as android.app.Application)
    app.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
  }

  private fun enableLocationProviders() {
    val locationManager =
      context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val shadow = shadowOf(locationManager)
    shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
  }

  // ===========================================================================
  // GeofencePolicy construction and defaults
  // ===========================================================================

  @Test
  fun `disabled policy has geofencing off with empty regions`() {
    val policy = GeofencePolicy.disabledDefaults()

    assertThat(policy.enabled).isFalse()
    assertThat(policy.regions).isEmpty()
    assertThat(policy.defaultPolicyProfile).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `default policy values are sensible`() {
    val policy = GeofencePolicy()

    assertThat(policy.enabled).isFalse()
    assertThat(policy.dwellTimeMs).isEqualTo(30_000L)
    assertThat(policy.locationAccuracyMeters).isEqualTo(100f)
    assertThat(policy.loiteringDelayMs).isEqualTo(30_000)
    assertThat(policy.notificationResponsiveness).isEqualTo(300_000)
  }

  @Test
  fun `enabled policy with regions preserves configuration`() {
    val policy = GeofencePolicy(
      enabled = true,
      regions = listOf(regionA, regionB),
      defaultPolicyProfile = "custom-default",
      dwellTimeMs = 60_000L,
      locationAccuracyMeters = 50f,
    )

    assertThat(policy.enabled).isTrue()
    assertThat(policy.regions).hasSize(2)
    assertThat(policy.defaultPolicyProfile).isEqualTo("custom-default")
    assertThat(policy.dwellTimeMs).isEqualTo(60_000L)
    assertThat(policy.locationAccuracyMeters).isEqualTo(50f)
  }

  // ===========================================================================
  // Manager initialization with disabled policy
  // ===========================================================================

  @Test
  fun `startMonitoring returns Disabled when policy is disabled`() = runTest {
    val manager = buildManager(policy = GeofencePolicy(enabled = false))

    val result = manager.startMonitoring()

    assertThat(result).isEqualTo(GeofenceStartResult.Disabled)
    assertThat(manager.isMonitoring()).isFalse()
  }

  @Test
  fun `manager starts with default profile name`() {
    val manager = buildManager()

    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `manager starts with no active regions`() {
    val manager = buildManager()

    assertThat(manager.getActiveRegionIds()).isEmpty()
  }

  @Test
  fun `manager starts not monitoring`() {
    val manager = buildManager()

    assertThat(manager.isMonitoring()).isFalse()
  }

  // ===========================================================================
  // Manager initialization with enabled policy
  // ===========================================================================

  @Test
  fun `startMonitoring returns NoRegions when region list is empty`() = runTest {
    val policy = GeofencePolicy(enabled = true, regions = emptyList())
    val manager = buildManager(policy = policy)

    val result = manager.startMonitoring()

    assertThat(result).isEqualTo(GeofenceStartResult.NoRegions)
    assertThat(manager.isMonitoring()).isFalse()
  }

  @Test
  fun `startMonitoring returns PermissionDenied without location permission`() = runTest {
    enableLocationProviders()
    val manager = buildManager()

    val result = manager.startMonitoring()

    assertThat(result).isInstanceOf(GeofenceStartResult.PermissionDenied::class.java)
    val denied = result as GeofenceStartResult.PermissionDenied
    assertThat(denied.permission).isEqualTo(Manifest.permission.ACCESS_FINE_LOCATION)
    assertThat(manager.isMonitoring()).isFalse()
  }

  @Test
  fun `startMonitoring returns LocationDisabled when providers are off`() = runTest {
    grantLocationPermission()
    // Explicitly disable both providers
    val locationManager =
      context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val shadow = shadowOf(locationManager)
    shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    val manager = buildManager()

    val result = manager.startMonitoring()

    assertThat(result).isInstanceOf(GeofenceStartResult.LocationDisabled::class.java)
    assertThat(manager.isMonitoring()).isFalse()
  }

  @Test
  fun `startMonitoring returns Success with permission and location enabled`() = runTest {
    grantLocationPermission()
    enableLocationProviders()
    val manager = buildManager()

    val result = manager.startMonitoring()

    assertThat(result).isEqualTo(GeofenceStartResult.Success)
    assertThat(manager.isMonitoring()).isTrue()
  }

  @Test
  fun `startMonitoring returns AlreadyActive when called twice`() = runTest {
    grantLocationPermission()
    enableLocationProviders()
    val manager = buildManager()

    manager.startMonitoring()
    val result = manager.startMonitoring()

    assertThat(result).isEqualTo(GeofenceStartResult.AlreadyActive)
    assertThat(manager.isMonitoring()).isTrue()
  }

  // ===========================================================================
  // Stop monitoring
  // ===========================================================================

  @Test
  fun `stopMonitoring sets monitoring to false`() = runTest {
    grantLocationPermission()
    enableLocationProviders()
    val manager = buildManager()
    manager.startMonitoring()
    assertThat(manager.isMonitoring()).isTrue()

    manager.stopMonitoring()

    assertThat(manager.isMonitoring()).isFalse()
  }

  @Test
  fun `stopMonitoring clears active regions`() = runTest {
    grantLocationPermission()
    enableLocationProviders()
    val manager = buildManager()
    manager.startMonitoring()
    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getActiveRegionIds()).isNotEmpty()

    manager.stopMonitoring()

    assertThat(manager.getActiveRegionIds()).isEmpty()
  }

  @Test
  fun `stopMonitoring resets profile to default`() = runTest {
    grantLocationPermission()
    enableLocationProviders()
    val manager = buildManager()
    manager.startMonitoring()
    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    manager.stopMonitoring()

    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `stopMonitoring is safe to call when not monitoring`() = runTest {
    val manager = buildManager()

    // Should not throw
    manager.stopMonitoring()

    assertThat(manager.isMonitoring()).isFalse()
  }

  // ===========================================================================
  // Transition handling: ENTER
  // ===========================================================================

  @Test
  fun `handleTransition ENTER adds region to active list`() = runTest {
    val manager = buildManager()

    manager.handleTransition("region-a", TransitionType.ENTER)

    assertThat(manager.getActiveRegionIds()).containsExactly("region-a")
    assertThat(manager.isInRegion("region-a")).isTrue()
  }

  @Test
  fun `handleTransition ENTER updates profile for highest priority region`() = runTest {
    val manager = buildManager()

    manager.handleTransition("region-a", TransitionType.ENTER)

    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")
  }

  @Test
  fun `handleTransition ENTER ignores unknown region ID`() = runTest {
    val manager = buildManager()

    manager.handleTransition("unknown-region", TransitionType.ENTER)

    assertThat(manager.getActiveRegionIds()).isEmpty()
    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `entering multiple regions activates both`() = runTest {
    val manager = buildManager()

    manager.handleTransition("region-a", TransitionType.ENTER)
    manager.handleTransition("region-b", TransitionType.ENTER)

    assertThat(manager.getActiveRegionIds()).containsExactly("region-a", "region-b")
    assertThat(manager.isInRegion("region-a")).isTrue()
    assertThat(manager.isInRegion("region-b")).isTrue()
  }

  @Test
  fun `entering lower priority region does not override higher priority profile`() = runTest {
    val manager = buildManager()

    // Enter higher priority first
    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    // Enter lower priority
    manager.handleTransition("region-b", TransitionType.ENTER)

    // Profile should remain as the higher-priority region-a's profile
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")
  }

  @Test
  fun `entering higher priority region overrides lower priority profile`() = runTest {
    val policy = GeofencePolicy(
      enabled = true,
      regions = listOf(regionA, regionB, regionC),
    )
    val manager = buildManager(policy = policy)

    // Enter lower priority first
    manager.handleTransition("region-b", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("warehouse")

    // Enter higher priority
    manager.handleTransition("region-c", TransitionType.ENTER)

    // Profile should switch to highest priority
    assertThat(manager.getCurrentProfileName()).isEqualTo("secure-zone")
  }

  // ===========================================================================
  // Transition handling: EXIT
  // ===========================================================================

  @Test
  fun `handleTransition EXIT removes region from active list`() = runTest {
    val manager = buildManager()
    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.isInRegion("region-a")).isTrue()

    manager.handleTransition("region-a", TransitionType.EXIT)

    assertThat(manager.isInRegion("region-a")).isFalse()
    assertThat(manager.getActiveRegionIds()).isEmpty()
  }

  @Test
  fun `exiting last region reverts to default policy profile`() = runTest {
    val manager = buildManager()
    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    manager.handleTransition("region-a", TransitionType.EXIT)

    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `exiting highest priority region switches to next highest`() = runTest {
    val manager = buildManager()
    manager.handleTransition("region-a", TransitionType.ENTER)
    manager.handleTransition("region-b", TransitionType.ENTER)
    // region-a has priority=10, region-b has priority=5
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    manager.handleTransition("region-a", TransitionType.EXIT)

    assertThat(manager.getCurrentProfileName()).isEqualTo("warehouse")
    assertThat(manager.getActiveRegionIds()).containsExactly("region-b")
  }

  @Test
  fun `exiting lower priority region does not change profile`() = runTest {
    val manager = buildManager()
    manager.handleTransition("region-a", TransitionType.ENTER)
    manager.handleTransition("region-b", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    manager.handleTransition("region-b", TransitionType.EXIT)

    // Profile should remain region-a's profile
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")
    assertThat(manager.getActiveRegionIds()).containsExactly("region-a")
  }

  @Test
  fun `handleTransition EXIT ignores unknown region ID`() = runTest {
    val manager = buildManager()
    manager.handleTransition("region-a", TransitionType.ENTER)

    manager.handleTransition("unknown-region", TransitionType.EXIT)

    // State should be unchanged
    assertThat(manager.getActiveRegionIds()).containsExactly("region-a")
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")
  }

  // ===========================================================================
  // Transition handling: DWELL
  // ===========================================================================

  @Test
  fun `handleTransition DWELL notifies listeners`() = runTest {
    val manager = buildManager()
    val dwellRegions = mutableListOf<GeofenceRegion>()
    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onDwell(region: GeofenceRegion) {
        dwellRegions.add(region)
      }
    })

    manager.handleTransition("region-a", TransitionType.DWELL)

    assertThat(dwellRegions).hasSize(1)
    assertThat(dwellRegions[0].id).isEqualTo("region-a")
  }

  @Test
  fun `handleTransition DWELL does not add region to active list`() = runTest {
    val manager = buildManager()

    manager.handleTransition("region-a", TransitionType.DWELL)

    // DWELL does not add to active regions (ENTER does)
    assertThat(manager.getActiveRegionIds()).isEmpty()
  }

  // ===========================================================================
  // Policy profile switching logic
  // ===========================================================================

  @Test
  fun `getCurrentProfile returns profile object for current name`() {
    val manager = buildManager()

    val profile = manager.getCurrentProfile()

    assertThat(profile).isNotNull()
    assertThat(profile?.name).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `getCurrentProfile returns null when provider has no match`() {
    val manager = GeofenceManager(
      context = context,
      policyProvider = { GeofencePolicy(enabled = true, regions = listOf(regionA)) },
      profileProvider = { null }, // Always returns null
      auditTrail = null,
      clock = fixedClock,
    )

    val profile = manager.getCurrentProfile()

    assertThat(profile).isNull()
  }

  @Test
  fun `getCurrentRegion returns null when no regions are active`() {
    val manager = buildManager()

    assertThat(manager.getCurrentRegion()).isNull()
  }

  @Test
  fun `getCurrentRegion returns highest priority active region`() = runTest {
    val manager = buildManager()
    manager.handleTransition("region-b", TransitionType.ENTER) // priority=5
    manager.handleTransition("region-a", TransitionType.ENTER) // priority=10

    val current = manager.getCurrentRegion()

    assertThat(current).isNotNull()
    assertThat(current?.id).isEqualTo("region-a")
  }

  @Test
  fun `profile updates correctly through enter-exit sequence`() = runTest {
    val policy = GeofencePolicy(
      enabled = true,
      regions = listOf(regionA, regionB, regionC),
    )
    val manager = buildManager(policy = policy)

    // Start with default
    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)

    // Enter region-b (priority=5)
    manager.handleTransition("region-b", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("warehouse")

    // Enter region-a (priority=10) - should switch
    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    // Enter region-c (priority=20) - should switch again
    manager.handleTransition("region-c", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("secure-zone")

    // Exit region-c - should fall back to region-a (priority=10)
    manager.handleTransition("region-c", TransitionType.EXIT)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    // Exit region-a - should fall back to region-b (priority=5)
    manager.handleTransition("region-a", TransitionType.EXIT)
    assertThat(manager.getCurrentProfileName()).isEqualTo("warehouse")

    // Exit region-b - should fall back to default
    manager.handleTransition("region-b", TransitionType.EXIT)
    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  // ===========================================================================
  // Listener management
  // ===========================================================================

  @Test
  fun `addTransitionListener receives ENTER notifications`() = runTest {
    val manager = buildManager()
    val enteredRegions = mutableListOf<String>()
    val appliedProfiles = mutableListOf<String>()
    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile) {
        enteredRegions.add(region.id)
        appliedProfiles.add(profile.name)
      }
    })

    manager.handleTransition("region-a", TransitionType.ENTER)

    assertThat(enteredRegions).containsExactly("region-a")
    assertThat(appliedProfiles).containsExactly("store-floor")
  }

  @Test
  fun `addTransitionListener receives EXIT notifications`() = runTest {
    val manager = buildManager()
    val exitedRegions = mutableListOf<String>()
    val newProfiles = mutableListOf<String>()
    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onRegionExited(region: GeofenceRegion, newProfile: PolicyProfile) {
        exitedRegions.add(region.id)
        newProfiles.add(newProfile.name)
      }
    })

    manager.handleTransition("region-a", TransitionType.ENTER)
    manager.handleTransition("region-a", TransitionType.EXIT)

    assertThat(exitedRegions).containsExactly("region-a")
    assertThat(newProfiles).containsExactly(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `multiple listeners all receive notifications`() = runTest {
    val manager = buildManager()
    val listener1Regions = mutableListOf<String>()
    val listener2Regions = mutableListOf<String>()

    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile) {
        listener1Regions.add(region.id)
      }
    })
    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile) {
        listener2Regions.add(region.id)
      }
    })

    manager.handleTransition("region-a", TransitionType.ENTER)

    assertThat(listener1Regions).containsExactly("region-a")
    assertThat(listener2Regions).containsExactly("region-a")
  }

  @Test
  fun `removeTransitionListener stops notifications`() = runTest {
    val manager = buildManager()
    val enteredRegions = mutableListOf<String>()
    val listener = object : GeofenceTransitionAdapter() {
      override fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile) {
        enteredRegions.add(region.id)
      }
    }
    manager.addTransitionListener(listener)

    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(enteredRegions).hasSize(1)

    manager.removeTransitionListener(listener)
    manager.handleTransition("region-b", TransitionType.ENTER)

    // Should still be 1 -- region-b notification was not delivered
    assertThat(enteredRegions).hasSize(1)
  }

  // ===========================================================================
  // Edge cases: empty geofence list, invalid regions
  // ===========================================================================

  @Test
  fun `isInRegion returns false for never-entered region`() {
    val manager = buildManager()

    assertThat(manager.isInRegion("region-a")).isFalse()
    assertThat(manager.isInRegion("nonexistent")).isFalse()
  }

  @Test
  fun `getActiveRegionIds returns empty list initially`() {
    val manager = buildManager()

    assertThat(manager.getActiveRegionIds()).isEmpty()
  }

  @Test
  fun `getCurrentRegion with empty policy returns null`() {
    val manager = buildManager(policy = GeofencePolicy(enabled = true, regions = emptyList()))

    assertThat(manager.getCurrentRegion()).isNull()
  }

  @Test
  fun `transition on region not in policy is ignored`() = runTest {
    val manager = buildManager(
      policy = GeofencePolicy(enabled = true, regions = listOf(regionA)),
    )

    // region-b is not in this policy
    manager.handleTransition("region-b", TransitionType.ENTER)

    assertThat(manager.getActiveRegionIds()).isEmpty()
    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `exit on profile matching current profile when other regions active falls back correctly`() =
    runTest {
      // Two regions with same policyProfile but different priorities
      val r1 = GeofenceRegion(
        id = "r1", latitude = 0.0, longitude = 0.0,
        radiusMeters = 200f, policyProfile = "shared-profile", priority = 5,
      )
      val r2 = GeofenceRegion(
        id = "r2", latitude = 1.0, longitude = 1.0,
        radiusMeters = 200f, policyProfile = "shared-profile", priority = 10,
      )
      val profilesWithShared = profiles + ("shared-profile" to PolicyProfile(name = "shared-profile"))
      val policy = GeofencePolicy(enabled = true, regions = listOf(r1, r2))
      val manager = GeofenceManager(
        context = context,
        policyProvider = { policy },
        profileProvider = { name -> profilesWithShared[name] },
        auditTrail = null,
        clock = fixedClock,
      )

      manager.handleTransition("r1", TransitionType.ENTER)
      manager.handleTransition("r2", TransitionType.ENTER)
      assertThat(manager.getCurrentProfileName()).isEqualTo("shared-profile")

      // Exit highest priority -- r1 still active with same profile
      manager.handleTransition("r2", TransitionType.EXIT)
      assertThat(manager.getCurrentProfileName()).isEqualTo("shared-profile")
      assertThat(manager.getActiveRegionIds()).containsExactly("r1")
    }

  @Test
  fun `policy with custom default profile uses that on exit`() = runTest {
    val customDefault = "custom-default"
    val policy = GeofencePolicy(
      enabled = true,
      regions = listOf(regionA),
      defaultPolicyProfile = customDefault,
    )
    val profilesWithCustom = profiles + (customDefault to PolicyProfile(name = customDefault))
    val manager = GeofenceManager(
      context = context,
      policyProvider = { policy },
      profileProvider = { name -> profilesWithCustom[name] },
      auditTrail = null,
      clock = fixedClock,
    )

    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")

    manager.handleTransition("region-a", TransitionType.EXIT)
    assertThat(manager.getCurrentProfileName()).isEqualTo(customDefault)
  }

  @Test
  fun `single region policy with enter and exit round-trip`() = runTest {
    val policy = GeofencePolicy(enabled = true, regions = listOf(regionA))
    val manager = buildManager(policy = policy)

    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)

    manager.handleTransition("region-a", TransitionType.ENTER)
    assertThat(manager.getCurrentProfileName()).isEqualTo("store-floor")
    assertThat(manager.getActiveRegionIds()).containsExactly("region-a")

    manager.handleTransition("region-a", TransitionType.EXIT)
    assertThat(manager.getCurrentProfileName()).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
    assertThat(manager.getActiveRegionIds()).isEmpty()
  }

  @Test
  fun `listener receives fallback profile on exit when profileProvider returns null`() = runTest {
    val policy = GeofencePolicy(enabled = true, regions = listOf(regionA))
    val manager = GeofenceManager(
      context = context,
      policyProvider = { policy },
      profileProvider = { null }, // No profiles available
      auditTrail = null,
      clock = fixedClock,
    )

    val exitProfiles = mutableListOf<String>()
    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onRegionExited(region: GeofenceRegion, newProfile: PolicyProfile) {
        exitProfiles.add(newProfile.name)
      }
    })

    manager.handleTransition("region-a", TransitionType.ENTER)
    manager.handleTransition("region-a", TransitionType.EXIT)

    // Even when profileProvider returns null, listener should get a PolicyProfile
    // constructed with the default profile name
    assertThat(exitProfiles).hasSize(1)
    assertThat(exitProfiles[0]).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `listener receives fallback profile on enter when profileProvider returns null`() = runTest {
    val policy = GeofencePolicy(enabled = true, regions = listOf(regionA))
    val manager = GeofenceManager(
      context = context,
      policyProvider = { policy },
      profileProvider = { null },
      auditTrail = null,
      clock = fixedClock,
    )

    val enterProfiles = mutableListOf<String>()
    manager.addTransitionListener(object : GeofenceTransitionAdapter() {
      override fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile) {
        enterProfiles.add(profile.name)
      }
    })

    manager.handleTransition("region-a", TransitionType.ENTER)

    // Fallback PolicyProfile(name = region.policyProfile) is used
    assertThat(enterProfiles).hasSize(1)
    assertThat(enterProfiles[0]).isEqualTo("store-floor")
  }
}
