plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

kotlin {
  jvmToolchain(17)
}

android {
  namespace = "com.sarastarquant.kioskops.sample"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.sarastarquant.kioskops.sample"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
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
}
