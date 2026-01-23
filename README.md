# KioskOps SDK for Android Enterprise

[![Build](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/build.yml)
[![CodeQL](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml/badge.svg)](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/pzverkov/KioskOps-SDK-Android-Enterprise/badge)](https://securityscorecards.dev/viewer/?uri=github.com/pzverkov/KioskOps-SDK-Android-Enterprise)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)

An **enterprise-grade Android SDK** for **offline-first operational events**, **local diagnostics**, and **fleet-friendly observability**. Designed for kiosk, retail, logistics, and field service deployments with **Samsung Knox / Android Enterprise** integration.

## Architecture

```
+-----------------------------------------------------------------------------------+
|                                   Host Application                                 |
+-----------------------------------------------------------------------------------+
        |                    |                    |                    |
        v                    v                    v                    v
+---------------+    +---------------+    +---------------+    +---------------+
|   enqueue()   |    |  heartbeat()  |    |   syncOnce()  |    |exportDiag()   |
+---------------+    +---------------+    +---------------+    +---------------+
        |                    |                    |                    |
        v                    v                    v                    v
+-----------------------------------------------------------------------------------+
|                              KioskOpsSdk (Singleton)                               |
+-----------------------------------------------------------------------------------+
        |                    |                    |                    |
        v                    v                    v                    v
+---------------+    +---------------+    +---------------+    +---------------+
|     Queue     |    |   Telemetry   |    |  SyncEngine   |    | Diagnostics   |
| (Room + AES)  |    | (Encrypted)   |    |  (OkHttp)     |    |  (ZIP Export) |
+---------------+    +---------------+    +---------------+    +---------------+
        |                    |                    |                    |
        v                    v                    v                    v
+-----------------------------------------------------------------------------------+
|                     Android Keystore (AES-GCM Hardware-Backed)                     |
+-----------------------------------------------------------------------------------+
```

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
                    kioskEnabled = true,
                    securityPolicy = SecurityPolicy.maximalistDefaults(),
                    syncPolicy = SyncPolicy(enabled = true)
                )
            }
        )
    }
}
```

### 3. Enqueue events

```kotlin
// Simple API (returns boolean)
val accepted = KioskOpsSdk.get().enqueue("button_press", """{"screen": "home"}""")

// Detailed API (returns rejection reason)
when (val result = KioskOpsSdk.get().enqueueDetailed("transaction", payload)) {
    is EnqueueResult.Accepted -> Log.d(TAG, "Queued: ${result.id}")
    is EnqueueResult.Rejected.PayloadTooLarge -> Log.w(TAG, "Payload too large")
    is EnqueueResult.Rejected.DenylistedKey -> Log.w(TAG, "PII detected: ${result.key}")
    is EnqueueResult.Rejected.QueueFull -> Log.w(TAG, "Queue at capacity")
}
```

## Features

### Security & Compliance

| Feature | Default | Description |
|---------|---------|-------------|
| Encryption at rest | On | AES-256-GCM via Android Keystore |
| PII denylist | On | Blocks common PII keys (email, phone, ssn, etc.) |
| Payload size limit | 64 KB | Configurable per deployment |
| Queue pressure control | 10K events / 50 MB | DROP_OLDEST, DROP_NEWEST, or BLOCK |
| Tamper-evident audit | On | Hash-chain with SHA-256 |
| Retention controls | 7-30 days | Configurable per data type |

### Fleet Operations

- **Policy drift detection** - Detects config changes with hash comparison
- **Device posture snapshot** - Device owner, lock-task mode, security patch level
- **Diagnostics export** - ZIP bundle with health snapshot, logs, telemetry, audit trail
- **Host-controlled upload** - SDK never auto-uploads; you control when/where

### Network Sync (Opt-in)

- **Disabled by default** - No silent off-device transfer
- **Batch ingest** - Configurable batch size with per-event acknowledgements
- **Exponential backoff** - 10s base, 6h max, with jitter
- **HMAC request signing** - Optional integrity protection
- **Poison event quarantine** - Non-retryable events excluded from sync

## Modules

| Module | Description |
|--------|-------------|
| `:kiosk-ops-sdk` | Core SDK (Android library AAR) |
| `:sample-app` | Reference integration |

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
| [Security & Compliance](docs/SECURITY_COMPLIANCE.md) | Threat model, defaults, limitations |
| [Target Use Cases](docs/SDK_TARGET.md) | What this SDK is (and isn't) for |
| [Server API Contract](docs/openapi.yaml) | OpenAPI spec for batch ingest |

## Known Limitations

- **Audit chain is process-local**: Restarts from GENESIS on app initialization
- **Lock-task mode**: Best-effort detection; not a full kiosk controller
- **Request signing**: Client-side only; server verification is your responsibility
- **Room migrations**: Provided for v2 -> v3 schema

## Roadmap

- [ ] mTLS patterns and certificate pinning hooks
- [ ] Schema-based event validation (replace denylist heuristics)
- [ ] Per-region endpoint routing helpers (EU/US/AU)
- [ ] Kotlin Multiplatform (iOS) support

## License

Business Source License 1.1 - Copyright (c) 2026 Petro Zverkov

Converts to Apache License 2.0 on January 1, 2032. See [LICENSE](LICENSE) for details.
