# KioskOps SDK for Android Enterprise

[![Build](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml)
[![CodeQL](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml)
[![Fuzz](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/fuzz.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/fuzz.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/pzverkov/KioskOps-SDK-Android-Enterprise/badge)](https://securityscorecards.dev/viewer/?uri=github.com/pzverkov/KioskOps-SDK-Android-Enterprise)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)

An **enterprise-grade Android SDK** for **offline-first operational events**, **local diagnostics**, and **fleet-friendly observability**. Designed for kiosk, retail, logistics, and field service deployments with **Samsung Knox / Android Enterprise** integration.

## Quick Start

### 1. Add dependency

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://your-maven-repo/releases") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.peterz.kioskops:kiosk-ops-sdk:0.1.0")
}
```

### 2. Initialize SDK

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        KioskOpsSdk.init(
            context = this,
            configProvider = {
                KioskOpsConfig(
                    baseUrl = "https://api.example.com/",
                    locationId = "STORE-001",
                    securityPolicy = SecurityPolicy.maximalistDefaults()
                )
            }
        )
    }
}
```

### 3. Enqueue events

```kotlin
val accepted = KioskOpsSdk.get().enqueue("button_press", """{"screen": "home"}""")
```

## Features

- **Encryption at rest** - AES-256-GCM via Android Keystore
- **PII filtering** - Automatic denylist for common PII keys
- **Tamper-evident audit** - SHA-256 hash-chain
- **Fleet operations** - Policy drift detection, device posture, diagnostics export
- **Network sync** - Opt-in batch sync with exponential backoff

See [Features](docs/FEATURES.md) for the complete list.

## Requirements

| Requirement | Version |
|-------------|---------|
| Android API | 26+ (Android 8.0) |
| Java | 17+ |
| Kotlin | 2.1+ |
| Gradle | 8.11+ |

## Documentation

| Document | Description |
|----------|-------------|
| [Integration Guide](docs/INTEGRATION_STEP_BY_STEP.md) | Step-by-step setup |
| [Architecture](docs/ARCHITECTURE.md) | System design and modules |
| [Features](docs/FEATURES.md) | Full feature list, limitations, roadmap |
| [Security & Compliance](docs/SECURITY_COMPLIANCE.md) | Threat model and security controls |
| [Target Use Cases](docs/SDK_TARGET.md) | What this SDK is (and isn't) for |
| [Server API Contract](docs/openapi.yaml) | OpenAPI spec for batch ingest |
| [Contributing](CONTRIBUTING.md) | Development setup and guidelines |

## License

Business Source License 1.1 - Copyright (c) 2026 Petro Zverkov

Converts to Apache License 2.0 on January 1, 2032. See [LICENSE](LICENSE) for details.
