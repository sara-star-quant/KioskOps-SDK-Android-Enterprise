// Pin transitive plugin-classpath dependencies with open CVEs.
// These ship in AGP/lint/layoutlib and other plugin classpaths; they never
// reach the published SDK AAR but flag in dependency-review because
// Dependabot attributes them to settings.gradle.kts (pluginManagement).
// bcprov/bcpkix < 1.84: GHSA-c3fc-8qff-9hwx, GHSA-wg6q-6289-32hp
// bcpg  1.80: Bouncy Castle Uncontrolled Resource Consumption (high)
// plexus-utils < 3.6.1: GHSA-6fmv-xxpf-w3cw directory traversal
buildscript {
  configurations.classpath {
    resolutionStrategy.force(
      "org.bouncycastle:bcprov-jdk18on:1.84",
      "org.bouncycastle:bcpkix-jdk18on:1.84",
      "org.bouncycastle:bcutil-jdk18on:1.84",
      "org.bouncycastle:bcpg-jdk18on:1.84",
      "org.codehaus.plexus:plexus-utils:3.6.1",
    )
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
  ignoredProjects += listOf("sample-app")
}
