plugins {
  alias(libs.plugins.android.application)
}

kotlin {
  jvmToolchain(21)
}

android {
  namespace = "com.sarastarquant.kioskops.sample"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.sarastarquant.kioskops.sample"
    minSdk = 33
    targetSdk = 36
    versionCode = 4
    versionName = "1.3.1"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
}

dependencies {
  implementation(project(":kiosk-ops-sdk"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines)
  implementation(libs.okhttp)
  // cuiDefaults() enables database encryption, which the SDK backs with SQLCipher.
  implementation(libs.sqlcipher.android)
  // The SDK schedules background work via WorkManager; the host provides its config.
  implementation(libs.androidx.work)
}
