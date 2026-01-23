plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

kotlin {
  jvmToolchain(17)
}

android {
  namespace = "com.peterz.kioskops.sdk"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
    debug {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  @Suppress("UnstableApiUsage")
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all { test ->
        test.useJUnitPlatform()
        // Exclude fuzz tests from regular test runs.
        // Jazzer's JUnit extension conflicts with Robolectric's sandbox classloader.
        // Run fuzz tests separately: ./gradlew :kiosk-ops-sdk:fuzzTest
        test.filter.excludeTestsMatching("com.peterz.kioskops.sdk.fuzz.*")
      }
    }
  }
}

// Disable release unit tests - minification strips classes needed for testing.
// Debug unit tests provide full coverage; release artifacts are validated via instrumented tests.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
  enabled = false
}

// Fuzz testing task - runs only fuzz tests, avoiding Robolectric conflicts.
// Usage: ./gradlew :kiosk-ops-sdk:fuzzTest
tasks.register<Test>("fuzzTest") {
  description = "Runs Jazzer fuzz tests"
  group = "verification"

  testClassesDirs = sourceSets["test"].output.classesDirs
  classpath = sourceSets["test"].runtimeClasspath

  useJUnitPlatform()
  filter.includeTestsMatching("com.peterz.kioskops.sdk.fuzz.*")
}

ksp {
  // Enables schema export for migration testing and enterprise auditability.
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
  arg("room.expandProjection", "true")
}

dependencies {
  api(libs.androidx.core.ktx)

  implementation(libs.androidx.startup)
  implementation(libs.androidx.work)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  implementation(libs.androidx.datastore)

  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit4)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)

  // Fuzzing (JUnit 5)
  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.vintage) // Run JUnit 4 tests via JUnit Platform
  testImplementation(libs.jazzer.junit)
}
