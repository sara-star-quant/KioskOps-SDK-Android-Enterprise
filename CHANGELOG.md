# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-01-23

Initial release of KioskOps SDK for Android Enterprise.

### Added

#### Security & Data Protection
- AES-256-GCM encryption at rest via Android Keystore
- Automatic PII filtering with configurable denylist
- Tamper-evident audit trail with SHA-256 hash chain
- Configurable payload size limits (default: 64 KB)

#### Event Queue
- Durable Room-based storage with encryption
- Queue pressure control (10K events / 50 MB default)
- Overflow strategies: `DROP_OLDEST`, `DROP_NEWEST`, `BLOCK`
- Configurable retention policies (7-30 days)

#### Fleet Operations
- Policy drift detection via config hash comparison
- Device posture snapshots (device owner, lock-task mode, security patch level)
- On-demand diagnostics export (ZIP bundle)
- Managed configuration support via Android Enterprise

#### Network Sync (Opt-in)
- Batch ingest with per-event acknowledgements
- Exponential backoff with jitter (10s base, 6h max)
- Optional HMAC request signing
- Poison event quarantine for non-retryable failures

#### Developer Experience
- Sample application with reference integration
- OpenAPI specification for server API contract
- Comprehensive documentation

### Requirements
- Android API 26+ (Android 8.0)
- Java 17+
- Kotlin 2.1+

[0.1.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.1.0
