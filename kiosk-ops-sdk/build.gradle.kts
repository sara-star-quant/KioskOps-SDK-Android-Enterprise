plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  `maven-publish`
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
// Configured after evaluation to access Android test task properties.
afterEvaluate {
  tasks.register<Test>("fuzzTest") {
    description = "Runs Jazzer fuzz tests"
    group = "verification"

    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = debugUnitTest.testClassesDirs
    classpath = debugUnitTest.classpath

    useJUnitPlatform()
    filter.includeTestsMatching("com.peterz.kioskops.sdk.fuzz.*")
  }
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

// Publishing configuration for GitHub Packages and JitPack
afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])

        groupId = "com.peterz.kioskops"
        artifactId = "kiosk-ops-sdk"
        version = findProperty("VERSION_NAME")?.toString() ?: "0.1.0-SNAPSHOT"

        pom {
          name.set("KioskOps SDK")
          description.set("Enterprise-grade Android SDK for offline-first operational events, local diagnostics, and fleet-friendly observability")
          url.set("https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise")

          licenses {
            license {
              name.set("Business Source License 1.1")
              url.set("https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/blob/main/LICENSE")
            }
          }

          developers {
            developer {
              id.set("pzverkov")
              name.set("Petro Zverkov")
            }
          }

          scm {
            url.set("https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise")
            connection.set("scm:git:git://github.com/pzverkov/KioskOps-SDK-Android-Enterprise.git")
            developerConnection.set("scm:git:ssh://github.com/pzverkov/KioskOps-SDK-Android-Enterprise.git")
          }
        }
      }
    }

    repositories {
      maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/pzverkov/KioskOps-SDK-Android-Enterprise")
        credentials {
          username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user")?.toString()
          password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.token")?.toString()
        }
      }
    }
  }
}
