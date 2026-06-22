plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.androidx.benchmark)
}

kotlin {
  jvmToolchain(21)
}

android {
  namespace = "com.sarastarquant.kioskops.benchmark"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    // Emulator results are directional only, not representative of real devices.
    // Suppress the hard stop so CI and local emulator runs still produce numbers;
    // treat physical-device runs as authoritative.
    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
  }

  // Microbenchmarks must run against release-quality code (R8, no debuggable flag).
  testBuildType = "release"

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }
}

dependencies {
  androidTestImplementation(project(":kiosk-ops-sdk"))
  androidTestImplementation(libs.androidx.benchmark.junit4)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.junit4)
}
