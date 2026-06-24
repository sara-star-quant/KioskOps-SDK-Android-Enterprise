// Pin transitive dependencies with open CVEs across all configurations.
// bcprov/bcpkix/bcutil/bcpg < 1.84: GHSA-c3fc-8qff-9hwx, GHSA-wg6q-6289-32hp, GHSA-p93r-85wp-75v3
// plexus-utils < 3.6.1: GHSA-6fmv-xxpf-w3cw
// netty < 4.1.135: GHSA-3qp7-7mw8-wx86, GHSA-x4gw-5cx5-pgmh, GHSA-5x3r-wrvg-rp6q, GHSA-c2gf-v879-257j
buildscript {
  configurations.classpath {
    resolutionStrategy.force(
      "org.bouncycastle:bcprov-jdk18on:1.84",
      "org.bouncycastle:bcpkix-jdk18on:1.84",
      "org.bouncycastle:bcutil-jdk18on:1.84",
      "org.bouncycastle:bcpg-jdk18on:1.84",
      "org.codehaus.plexus:plexus-utils:4.0.3",
    )
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  // Load the publish plugin at the root (applied in kiosk-ops-sdk) so the
  // MavenCentralBuildService loads under a single classloader.
  alias(libs.plugins.maven.publish) apply false
}

subprojects {
  configurations.configureEach {
    resolutionStrategy.force(
      "org.bouncycastle:bcprov-jdk18on:1.84",
      "org.bouncycastle:bcpkix-jdk18on:1.84",
      "org.bouncycastle:bcutil-jdk18on:1.84",
      "org.bouncycastle:bcpg-jdk18on:1.84",
    )
    // netty arrives only via grpc-netty in the Android Unified Test Platform
    // configurations (test-only; absent from releaseRuntimeClasspath, never in the AAR).
    // Pin the whole io.netty family so the modules stay version-aligned.
    resolutionStrategy.eachDependency {
      if (requested.group == "io.netty") {
        useVersion("4.1.135.Final")
      }
      // jackson arrives only via the Dokka generator runtime (doc build); absent
      // from releaseRuntimeClasspath, never in the AAR. Pin the whole jackson
      // stack (not just databind) so the modules stay aligned, which jackson
      // requires. 2.18.8 is the latest 2.18.x and clears GHSA-hgj6-7826-r7m5,
      // GHSA-rmj7-2vxq-3g9f, and GHSA-j3rv-43j4-c7qm. GHSA-5jmj-h7xm-6q6v (the old
      // polymorphic-deserialization advisory) has no published fix and does not
      // apply here: Dokka does not enable default typing on untrusted input.
      if (requested.group.startsWith("com.fasterxml.jackson")) {
        useVersion("2.18.8")
      }
      // wire-runtime arrives only via androidx.benchmark tooling (test/benchmark
      // module; never in the AAR). Pin the whole group so wire-runtime and its
      // -jvm variant stay aligned. 6.3.0 fixes GHSA-7xpr-hc2w-34m9.
      if (requested.group == "com.squareup.wire") {
        useVersion("6.3.0")
      }
    }
  }
}
