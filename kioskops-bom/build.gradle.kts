// Bill of Materials: lets consumers align KioskOps artifact versions in one place.
// Today it constrains the single published library; the module exists so multi-artifact
// coordination (planned splits) does not require a consumer-facing breaking change later.
plugins {
  `java-platform`
  alias(libs.plugins.maven.publish)
}

val sdkVersion = findProperty("VERSION_NAME")?.toString() ?: "0.1.0-SNAPSHOT"

dependencies {
  constraints {
    api("com.sarastarquant.kioskops:kiosk-ops-sdk:$sdkVersion")
  }
}

// Maven Central publishing via Vanniktech plugin (POM-only platform artifact)
mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  coordinates(
    groupId = "com.sarastarquant.kioskops",
    artifactId = "kioskops-bom",
    version = sdkVersion,
  )

  pom {
    name.set("KioskOps SDK BOM")
    description.set("Bill of Materials for coordinated versioning of KioskOps SDK artifacts")
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
