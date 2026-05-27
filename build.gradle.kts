// Pin transitive dependencies with open CVEs across all configurations.
// bcprov/bcpkix/bcutil/bcpg < 1.84: GHSA-c3fc-8qff-9hwx, GHSA-wg6q-6289-32hp, GHSA-p93r-85wp-75v3
// plexus-utils < 3.6.1: GHSA-6fmv-xxpf-w3cw
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
}

subprojects {
  configurations.configureEach {
    resolutionStrategy.force(
      "org.bouncycastle:bcprov-jdk18on:1.84",
      "org.bouncycastle:bcpkix-jdk18on:1.84",
      "org.bouncycastle:bcutil-jdk18on:1.84",
      "org.bouncycastle:bcpg-jdk18on:1.84",
    )
  }
}
