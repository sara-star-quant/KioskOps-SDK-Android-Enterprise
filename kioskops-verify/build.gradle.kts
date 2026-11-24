plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.detekt)
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
  }
  jvm { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
  android {
    namespace = "com.sarastarquant.kioskops.verify"
    compileSdk = 37
    minSdk = 33
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
  iosArm64()
  iosSimulatorArm64()

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
  abiValidation { }

  sourceSets {
    commonMain.dependencies { api(libs.kotlinx.serialization.json) }
    commonTest.dependencies { implementation(kotlin("test")) }
    // The JCA implementation is the same file for the jvm and android targets.
    jvmMain { kotlin.srcDir("src/jvmShared/kotlin") }
    androidMain { kotlin.srcDir("src/jvmShared/kotlin") }
  }
}

// One JSON file is the source of the vectors; the common test reads it as a Kotlin constant so the
// same test runs on every target, including iOS where classpath resources do not exist.
val generateGoldenVectors by tasks.registering {
  val json = layout.projectDirectory.file("vectors/golden_vectors.json")
  val out = layout.buildDirectory.dir("generated/goldenVectors")
  inputs.file(json)
  outputs.dir(out)
  doLast {
    val text = json.asFile.readText()
    check('$' !in text && "\"\"\"" !in text) { "vectors contain a character a raw string cannot hold" }
    val dir = out.get().asFile.resolve("com/sarastarquant/kioskops/sdk/audit/core")
    dir.mkdirs()
    dir.resolve("GoldenVectorsData.kt").writeText(
      "package com.sarastarquant.kioskops.sdk.audit.core\n\ninternal const val GOLDEN_VECTORS_JSON: String = \"\"\"" + text + "\"\"\"\n",
    )
  }
}
kotlin.sourceSets.commonTest { kotlin.srcDir(generateGoldenVectors) }

detekt {
  source.setFrom("src/commonMain/kotlin", "src/jvmShared/kotlin", "src/iosMain/kotlin", "src/commonTest/kotlin")
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
