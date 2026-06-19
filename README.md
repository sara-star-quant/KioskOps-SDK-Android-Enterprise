# KioskOps SDK for Android Enterprise

[![Build](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml/badge.svg)](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/pzverkov/e10185573398e3034647f57ff8d61076/raw/kioskops-coverage.json)](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml)
[![CodeQL](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml/badge.svg)](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml)
[![Fuzz](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/fuzz.yml/badge.svg)](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/actions/workflows/fuzz.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/badge)](https://securityscorecards.dev/viewer/?uri=github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise)
[![Maven Central](https://img.shields.io/maven-central/v/com.sarastarquant.kioskops/kiosk-ops-sdk.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.sarastarquant.kioskops/kiosk-ops-sdk)
[![JitPack](https://jitpack.io/v/sara-star-quant/KioskOps-SDK-Android-Enterprise.svg)](https://jitpack.io/#sara-star-quant/KioskOps-SDK-Android-Enterprise)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)](https://developer.android.com/about/versions/13)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Docs](https://img.shields.io/badge/Docs-Dokka-brightgreen.svg)](https://sara-star-quant.github.io/KioskOps-SDK-Android-Enterprise/)

An **enterprise-grade Android SDK** for **offline-first operational events**, **local diagnostics**, and **fleet-friendly observability**. Designed for kiosk, retail, logistics, and field service deployments with **Samsung Knox / Android Enterprise** integration.

API reference: [sara-star-quant.github.io/KioskOps-SDK-Android-Enterprise](https://sara-star-quant.github.io/KioskOps-SDK-Android-Enterprise/) (auto-published from `main` and release tags).

> **Disclaimer:** Security controls and regulatory-framework references are engineering
> aids only, not legal, compliance, or security advice. See [docs/LEGAL.md](docs/LEGAL.md).

## Installation

### Option A: Maven Central (Recommended)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.sarastarquant.kioskops:kiosk-ops-sdk:1.3.0")
}
```

Optionally pin the version once via the BOM (`kioskops-bom`); see
[Integration](docs/INTEGRATION.md#optional-bom-for-coordinated-versions) for the BOM and
Gradle version-catalog snippets.

### Option B: GitHub Packages

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.sarastarquant.kioskops:kiosk-ops-sdk:1.3.0")
}
```

### Option C: JitPack

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.sara-star-quant:KioskOps-SDK-Android-Enterprise:v1.3.0")
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
// suspend function, call from a coroutine scope
lifecycleScope.launch {
    val accepted = KioskOpsSdk.get().enqueue("button_press", """{"screen": "home"}""")
}

// or use the blocking wrapper from Java / non-coroutine code
val accepted = KioskOpsSdk.get().enqueueBlocking("button_press", """{"screen": "home"}""").get()
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
| Android API | 33+ (Android 13) |
| Java | 17+ |
| Kotlin | 2.1+ |
| Gradle | 8.11+ |

## Documentation

| Document | Description |
|----------|-------------|
| [Integration Guide](docs/INTEGRATION.md) | Step-by-step setup |
| [Architecture](docs/ARCHITECTURE.md) | System design, modules, and scope |
| [Features](docs/FEATURES.md) | Full feature list and limitations |
| [Security and Compliance](docs/SECURITY_COMPLIANCE.md) | Threat model, transport security, key management, audit integrity |
| [Compliance Mappings](docs/compliance/) | NIST 800-171, CJIS, ASD, BSI, Australian Privacy Act |
| [Server API Contract](docs/openapi.yaml) | OpenAPI spec for batch ingest |
| [Changelog](CHANGELOG.md) | Version history |
| [Roadmap](ROADMAP.md) | Planned features |
| [Go-To-Market](kioskops-gtm.md) | Positioning, target buyer, pricing model, and rollout plan |
| [Contributing](CONTRIBUTING.md) | Development setup and guidelines |

## License

Business Source License 1.1 - Copyright (c) 2026 SARA STAR QUANT LLC

Converts to Apache License 2.0 on January 1, 2032. See [LICENSE](LICENSE) for details.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
The authors and contributors shall not be held liable for any damages arising from
the use of this software. See LICENSE for full terms.
