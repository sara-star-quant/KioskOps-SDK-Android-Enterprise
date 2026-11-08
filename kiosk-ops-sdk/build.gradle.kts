import org.jetbrains.kotlin.abi.tools.AbiFilters
import org.jetbrains.kotlin.abi.tools.AbiTools

// Build-tooling plugins (dokka, kover, cyclonedx) bring jackson and freemarker onto this
// module's plugin classpath. Never in the AAR; pinned to patched versions. jackson-annotations
// is published as major.minor only (2.21), without patch releases. guava is pinned for the same reason.
buildscript {
  dependencies {
    // Kotlin's ABI dump engine for the public API guard below. Keep in step with `kotlin` in
    // libs.versions.toml. Build tooling only; never in the AAR.
    classpath("org.jetbrains.kotlin:abi-tools:2.4.21")
  }
  configurations.classpath {
    resolutionStrategy.eachDependency {
      if (requested.group.startsWith("com.fasterxml.jackson")) {
        useVersion(if (requested.name == "jackson-annotations") "2.21" else "2.21.8")
      }
      if (requested.group == "org.freemarker") {
        useVersion("2.3.35")
      }
      if (requested.group == "com.google.guava" && requested.name == "guava") {
        useVersion("33.8.0-jre")
      }
    }
  }
}

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.dokka)
  alias(libs.plugins.detekt)
  alias(libs.plugins.kover)
  alias(libs.plugins.cyclonedx)
  alias(libs.plugins.maven.publish)
}

kotlin {
  jvmToolchain(21)
  // Keep the published metadata and stdlib API at the documented Kotlin floor (README
  // Requirements) so consumers on older compilers can still read the artifact.
  compilerOptions {
    // Build on JDK 21 but emit Java 17 bytecode (README Requirements: Java 17+).
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
  }
}

android {
  namespace = "com.sarastarquant.kioskops.sdk"
  compileSdk = 37

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  defaultConfig {
    minSdk = 33
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "SDK_VERSION", "\"${findProperty("VERSION_NAME") ?: "0.0.0-SNAPSHOT"}\"")
  }

  buildFeatures {
    buildConfig = true
  }

  buildTypes {
    release {
      // Libraries must not minify themselves; consumer R8 handles shrinking.
      // consumer-rules.pro tells the consumer what to keep.
      isMinifyEnabled = false
    }
    debug {
      isMinifyEnabled = false
    }
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
        test.filter.excludeTestsMatching("com.sarastarquant.kioskops.sdk.fuzz.*")
        // Locate compliance docs for the traceability check; pass -Pcompliance.update
        // to regenerate docs/compliance/traceability.md instead of verifying it.
        test.systemProperty("compliance.docsDir", "${rootProject.projectDir}/docs/compliance")
        test.systemProperty("compliance.update", project.hasProperty("compliance.update").toString())
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
    filter.includeTestsMatching("com.sarastarquant.kioskops.sdk.fuzz.*")
  }
}

ksp {
  // Enables schema export for migration testing and enterprise auditability.
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
  arg("room.expandProjection", "true")
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
  baseline = file("$rootDir/config/detekt/baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  reports {
    sarif.required.set(true)
  }
}

kover {
  reports {
    verify {
      rule {
        minBound(70)
      }
    }
  }
}

dependencies {
  api(libs.androidx.core.ktx)

  implementation(libs.androidx.startup)
  implementation(libs.androidx.lifecycle.process)
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
  testImplementation(libs.classgraph)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.androidx.room.testing)

  // Instrumented tests (androidTest)
  androidTestImplementation(libs.junit4)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.work.testing)
  // Exercises the real SQLCipher-backed database path (cuiDefaults/cjisDefaults) on-device.
  androidTestImplementation(libs.sqlcipher.android)
  // Drives the sync pipeline against a local server on-device.
  androidTestImplementation(libs.okhttp.mockwebserver)

  // Fuzzing (JUnit 5)
  testImplementation(libs.junit5.api)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit5.vintage) // Run JUnit 4 tests via JUnit Platform
  testImplementation(libs.jazzer.junit)
}

// Javadoc JAR from Dokka output (required by Maven Central)
val javadocJar by tasks.registering(Jar::class) {
  archiveClassifier.set("javadoc")
  dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
  from(layout.buildDirectory.dir("dokka/html"))
}

// Maven Central publishing via Vanniktech plugin (handles Central Portal bundle upload)
mavenPublishing {
  publishToMavenCentral()
  // Sign only when a key is available. Maven Central requires signatures and the
  // release workflow provides signingInMemoryKey. JitPack and local publishToMavenLocal
  // builds have no key, so signAllPublications() there fails with "no configured signatory".
  if (project.hasProperty("signingInMemoryKey")) {
    signAllPublications()
  }

  coordinates(
    groupId = "com.sarastarquant.kioskops",
    artifactId = "kiosk-ops-sdk",
    version = findProperty("VERSION_NAME")?.toString() ?: "0.1.0-SNAPSHOT",
  )

  pom {
    name.set("KioskOps SDK")
    description.set("Enterprise-grade Android SDK for offline-first operational events, local diagnostics, and fleet-friendly observability")
    url.set("https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise")

    licenses {
      license {
        name.set("Business Source License 1.1")
        url.set("https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/blob/main/LICENSE")
      }
    }

    developers {
      developer {
        id.set("pzverkov")
        name.set("Petro Zverkov")
        organization.set("Sara Star Quant LLC")
      }
    }

    scm {
      url.set("https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise")
      connection.set("scm:git:git://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise.git")
      developerConnection.set("scm:git:ssh://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise.git")
    }
  }
}

// GitHub Packages repository (secondary distribution)
afterEvaluate {
  publishing {
    repositories {
      maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise")
        credentials {
          username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user")?.toString()
          password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.token")?.toString()
        }
      }
    }
  }
}

// Public API guard. Dumps the release AAR's public API in BCV text format and compares it with
// the committed api/kiosk-ops-sdk.api. `checkApi` fails on any difference (wired into `check`);
// run `updateApi` after an intentional API change and commit the result.
abstract class ApiDumpTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val classes: ConfigurableFileCollection

  @get:OutputFile
  abstract val dump: RegularFileProperty

  @TaskAction
  fun dumpApi() {
    val out = StringBuilder()
    AbiTools.getInstance().printJvmDump(out, classes.files, AbiFilters(emptySet(), setOf("com.sarastarquant.kioskops.sdk.R"), emptySet(), emptySet()))
    dump.get().asFile.writeText(out.toString())
  }
}

val apiReference = layout.projectDirectory.file("api/kiosk-ops-sdk.api")

val dumpApi by tasks.registering(ApiDumpTask::class) {
  classes.from(tasks.named("bundleLibCompileToJarRelease"))
  dump.set(layout.buildDirectory.file("api/kiosk-ops-sdk.api"))
}

val updateApi by tasks.registering(Copy::class) {
  from(dumpApi.map { it.dump })
  into(apiReference.asFile.parentFile)
}

abstract class ApiCheckTask : DefaultTask() {
  @get:InputFile
  abstract val reference: RegularFileProperty

  @get:InputFile
  abstract val actual: RegularFileProperty

  @TaskAction
  fun check() {
    val diff = AbiTools.getInstance().filesDiff(reference.get().asFile, actual.get().asFile)
    if (!diff.isNullOrBlank()) {
      throw GradleException("Public API changed. Run :kiosk-ops-sdk:updateApi if intended.\n$diff")
    }
  }
}

val checkApi by tasks.registering(ApiCheckTask::class) {
  reference.set(apiReference)
  actual.set(dumpApi.flatMap { it.dump })
}

tasks.named("check") { dependsOn(checkApi) }
