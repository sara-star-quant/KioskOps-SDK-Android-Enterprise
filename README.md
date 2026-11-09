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

**An offline-first event pipeline with a tamper-evident audit trail, for regulated device fleets.**
Devices lose network and keep recording: events are encrypted at rest, validated, PII-screened, hash-chained, and synced when connectivity returns.
Android today (API 33+), iOS in preview from v1.6.0.

API reference: [sara-star-quant.github.io/KioskOps-SDK-Android-Enterprise](https://sara-star-quant.github.io/KioskOps-SDK-Android-Enterprise/) (auto-published from `main` and release tags).

> **Disclaimer:** Security controls and regulatory-framework references are engineering
> aids only, not legal, compliance, or security advice. See [docs/LEGAL.md](docs/LEGAL.md).

## Scope

KioskOps is an embedded data-layer and audit SDK. It protects the events your app produces, records that they were not altered, and maps its controls to compliance frameworks.

| It is | It is not |
|-------|-----------|
| An encrypted, offline-first event queue with batch sync and backoff | A RASP or app-shielding product |
| A SHA-256 hash-chained audit trail that survives restarts | An obfuscator, packer, or anti-repackaging tool |
| Schema validation, PII detection and redaction, anomaly scoring | A replacement for Knox, Zebra, or your MDM |
| Compliance presets and control mappings (FedRAMP, NIST 800-171, CJIS, GDPR, ASD E8) | Certified; no FIPS 140-3, Common Criteria, or EMVCo claim |
| Device posture and key-attestation reporting for your server to verify | An authentication or identity product (no FIDO, no transaction signing) |
| Signed releases with build provenance and an SBOM | A guarantee of compliance; see the disclaimer above |

Apps that run in a hostile runtime, such as banking and payment apps, need a RASP and identity vendor like OneSpan or Build38. KioskOps runs alongside one; it does not replace it.

## Differentiators

- **Offline by design.** Events are durable and acknowledged per event, with delivery callbacks. Most mobile security SDKs assume connectivity.
- **Audit evidence.** The hash chain, retention reports, and data-rights APIs (export, delete, wipe) produce artifacts an auditor can read.
- **Compliance presets.** `fedRampDefaults`, `gdprDefaults`, `cuiDefaults`, `cjisDefaults`, and `asdEssentialEightDefaults` set encryption, validation, PII handling, and retention together.
- **Supply chain.** Releases are signed with Sigstore and carry SLSA build provenance and a CycloneDX SBOM. The project is tracked on OpenSSF Scorecard. Source is available under BSL 1.1.
- **Size.** The AAR has a 1.5 MB CI budget.

## Alternatives

| Alternative | Strength | Gap relative to KioskOps |
|-------------|----------|--------------------------|
| WorkManager + Room + custom crypto | Full control | You build and maintain the audit chain, retention, PII screening, data-rights APIs, and key lifecycle |
| MDM / OEM stack (Knox, Zebra, Intune, Esper, SOTI) | Device control, policy push, deployment | No application-level event durability or app-produced audit trail. KioskOps reads posture from these and does not replace them |
| Crash and telemetry SDKs (Sentry, Datadog, Firebase) | Dashboards, crash grouping, ecosystem | No local-first retention, PII rejection before upload, or framework mappings |
| Offline sync databases (Couchbase Lite, Ditto) | Bidirectional sync, conflict resolution | No audit chain or compliance posture. KioskOps is an event pipeline, not a database |
| RASP and identity vendors (OneSpan, Build38, Promon) | Runtime protection, shielding, device binding, authentication | No offline event auditing or compliance presets. Run one alongside KioskOps if the threat model includes a hostile runtime |

### Poor fit

- You need a hosted dashboard. KioskOps ships the client and an [ingest contract](docs/openapi.yaml); you operate the backend.
- Devices are always online and there is no audit requirement. A telemetry SDK is simpler.
- The main threat is an attacker on the device (repackaging, hooking, credential theft). Start with RASP.
- You need a certified cryptographic module. None is claimed.

## Security posture

See [Security and Compliance](docs/SECURITY_COMPLIANCE.md) for the threat model.

| Area | Status |
|------|--------|
| Data at rest | AES-256-GCM payloads with Android Keystore keys; SQLCipher database encryption on by default in the CUI and CJIS presets |
| Transport | TLS with certificate pinning and Certificate Transparency checks; full SCT signature verification planned |
| Device attestation | Keystore key attestation reported; Play Integrity and App Attest providers planned |
| Runtime tamper and root signals | Planned as advisory audit events. Local checks are bypassable and are not a security boundary |
| OWASP MASVS | Per-control mapping planned, with gaps listed |
| Independent penetration test | Not commissioned |

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
    implementation("com.sarastarquant.kioskops:kiosk-ops-sdk:1.5.0")
}
```

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
    implementation("com.sarastarquant.kioskops:kiosk-ops-sdk:1.5.0")
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
    implementation("com.github.sara-star-quant:KioskOps-SDK-Android-Enterprise:v1.5.0")
}
```

## Quick Start

> Want to see it first? A runnable demo APK ships with each [release](https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise/releases); see [Sample app](docs/sample-app.md) for screenshots and a walkthrough.

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
- **Config presets** - `fedRampDefaults()`, `gdprDefaults()`, `cuiDefaults()`, `cjisDefaults()`, `asdEssentialEightDefaults()`

See [Features](docs/FEATURES.md) for the complete list.

## Performance

Microbenchmarks of the per-event hot paths (emulator `sdk_gphone64_arm64`, API 34;
directional, not production hardware):

| Operation | Median | Throughput |
|-----------|--------|------------|
| Anomaly scoring (per event) | 4.8 us | ~207k events/s |
| Field encryption, 3 fields (AES-256-GCM) | 46.1 us | ~22k ops/s |
| Field decryption, 3 fields | 39.2 us | ~25k ops/s |

See [Benchmarks](docs/benchmarks.md) for methodology and how to reproduce.

## Requirements

| Requirement | Version |
|-------------|---------|
| Android API | 33+ (Android 13) |
| Java | 17+ |
| Kotlin | 2.3+ |
| Gradle | 8.11+ |

## Documentation

| Document | Description |
|----------|-------------|
| [Integration Guide](docs/INTEGRATION.md) | Step-by-step setup |
| [Architecture](docs/ARCHITECTURE.md) | System design, modules, and scope |
| [Features](docs/FEATURES.md) | Full feature list and limitations |
| [Benchmarks](docs/benchmarks.md) | Microbenchmark results and reproduction |
| [Sample app](docs/sample-app.md) | Runnable demo, screenshots, and integration notes |
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
