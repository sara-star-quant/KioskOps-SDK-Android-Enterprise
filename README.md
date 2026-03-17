# KioskOps SDK for Android Enterprise

[![Build](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml)
[![CodeQL](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml)
[![Fuzz](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/fuzz.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/fuzz.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/pzverkov/KioskOps-SDK-Android-Enterprise/badge)](https://securityscorecards.dev/viewer/?uri=github.com/pzverkov/KioskOps-SDK-Android-Enterprise)
[![JitPack](https://jitpack.io/v/pzverkov/KioskOps-SDK-Android-Enterprise.svg)](https://jitpack.io/#pzverkov/KioskOps-SDK-Android-Enterprise)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)

An **enterprise-grade Android SDK** for **offline-first operational events**, **local diagnostics**, and **fleet-friendly observability**. Designed for kiosk, retail, logistics, and field service deployments with **Samsung Knox / Android Enterprise** integration.

> **Disclaimer:** This SDK provides security controls and references regulatory frameworks
> (NIST 800-53, FedRAMP, GDPR, ISO 27001) as engineering aids only. It is not legal,
> compliance, or security advice. The authors assume no responsibility for regulatory
> outcomes. You are solely responsible for evaluating whether this software meets your
> organization's legal, security, and compliance requirements. Consult qualified
> professionals before deploying in regulated environments. See [LICENSE](LICENSE) for
> warranty and liability terms.

## Installation

### Option A: GitHub Packages (Recommended)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/pzverkov/KioskOps-SDK-Android-Enterprise")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.peterz.kioskops:kiosk-ops-sdk:0.5.1")
}
```

### Option B: JitPack

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.pzverkov:KioskOps-SDK-Android-Enterprise:v0.5.1")
}
```

## Quick Start

### 1. Initialize SDK

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

### 2. Enqueue events

```kotlin
val accepted = KioskOpsSdk.get().enqueue("button_press", """{"screen": "home"}""")
```

## Features

- **Encryption at rest** - AES-256-GCM via Android Keystore; field-level encryption for sensitive attributes
- **PII detection and redaction** - Regex-based scanner with REJECT/REDACT/FLAG actions; pluggable detector interface
- **Event validation** - JSON Schema-based validation with strict/permissive modes
- **Anomaly detection** - Statistical detector for payload size, event rate, schema deviation, and cardinality tracking
- **Tamper-evident audit** - SHA-256 hash-chain, Room-backed, persistent across restarts
- **GDPR compliance APIs** - User data export (Art. 20), deletion (Art. 17), full device wipe
- **Fleet operations** - Policy drift detection, device posture, diagnostics export, remote config
- **Network sync** - Opt-in batch sync with exponential backoff
- **Config presets** - `fedRampDefaults()` and `gdprDefaults()` for common compliance profiles

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
| [Features](docs/FEATURES.md) | Full feature list and limitations |
| [Security & Compliance](docs/SECURITY_COMPLIANCE.md) | Threat model and security controls |
| [Target Use Cases](docs/SDK_TARGET.md) | What this SDK is (and isn't) for |
| [Server API Contract](docs/openapi.yaml) | OpenAPI spec for batch ingest |
| [Changelog](CHANGELOG.md) | Version history |
| [Roadmap](ROADMAP.md) | Planned features |
| [Contributing](CONTRIBUTING.md) | Development setup and guidelines |

## License

Business Source License 1.1 - Copyright (c) 2026 SARA STAR QUANT LLC

Converts to Apache License 2.0 on January 1, 2032. See [LICENSE](LICENSE) for details.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
The authors and contributors shall not be held liable for any damages arising from
the use of this software. See LICENSE for full terms.
