# ASD Essential Eight -- KioskOps SDK Control Mapping

> **Disclaimer:** This document is an engineering reference only. It does not
> constitute a compliance certification, legal advice, or security assessment.
> The SDK has not been independently audited or assessed against the Australian
> Signals Directorate Essential Eight Maturity Model. You must conduct your own
> assessment with qualified professionals. The Essential Eight covers
> organizational-level strategies; an SDK can only contribute to a subset of
> each strategy.

## Scope

The ASD Essential Eight is a prioritized set of mitigation strategies for
cyber security incidents. This mapping shows where the SDK provides supporting
technical controls for each strategy. The `asdEssentialEightDefaults()` config
preset enables the recommended combination.

---

## Strategy Mapping

| # | Strategy | SDK Contribution | Implementation | Notes/Limitations |
|---|----------|-----------------|----------------|-------------------|
| 1 | Application Control | Event validation; anomaly detection | JSON Schema validation rejects malformed or unexpected event types; anomaly detection flags unusual patterns; `cjisDefaults()` and `asdEssentialEightDefaults()` restrict SDK to approved operations | Application control (allowlisting executables) is an OS/MDM responsibility; SDK controls its own input pipeline |
| 2 | Patch Applications | CycloneDX SBOM | BOM generated in CI pipeline lists all SDK dependencies with versions for vulnerability scanning; dependency updates tracked in CHANGELOG | SDK consumers must monitor the SBOM and update the SDK dependency when patches are released |
| 3 | Configure Microsoft Office Macros | N/A | Not applicable to Android SDK | This strategy targets desktop office productivity software |
| 4 | User Application Hardening | Certificate pinning; Certificate Transparency; debug restriction | SHA-256 certificate pins prevent MITM; CT log validation; debug log level restricted to debug builds only; no WebView or browser components in SDK | Host app must harden its own UI components, WebViews, and third-party libraries |
| 5 | Restrict Administrative Privileges | Config presets; data classification | `asdEssentialEightDefaults()` applies least-privilege SDK configuration; data classification tags restrict access to CONFIDENTIAL/RESTRICTED data | OS-level admin privilege restriction is MDM/EMM responsibility; SDK restricts its own operational scope |
| 6 | Patch Operating Systems | Device posture snapshot | Security patch level reported in device posture; host app can gate operations on patch currency | OS patching is device management responsibility; SDK provides posture data for policy decisions |
| 7 | Multi-factor Authentication | mTLS | Client certificate mutual authentication provides a second authentication factor at the transport layer | MFA for end users is host-app responsibility; SDK provides the mTLS transport channel |
| 8 | Regular Backups | Data export API; retention controls | `exportUserData()` provides data portability (ZIP); configurable retention policies per data type; 365-day minimum audit retention | SDK manages local data only; backup strategy for backend systems is infrastructure responsibility |

---

## Maturity Level Alignment

The Essential Eight Maturity Model defines three levels (ML1, ML2, ML3). The
SDK's contributions primarily support ML1 and ML2 posture:

| Maturity Level | SDK Support |
|---------------|-------------|
| ML1 (partly aligned) | Event validation, SBOM generation, certificate pinning, encryption at rest, debug build restrictions |
| ML2 (mostly aligned) | Anomaly detection, mTLS, data classification, audit trail with integrity verification, device posture reporting |
| ML3 (fully aligned) | SDK provides supporting controls; ML3 requires organizational-level threat hunting, SOC integration, and continuous monitoring beyond SDK scope |

---

## Controls outside SDK scope

The following aspects of the Essential Eight require host-app, infrastructure,
or organizational measures:

- **Application allowlisting/blocklisting** -- OS or MDM policy
- **OS patching and patch verification** -- device management
- **MFA enrollment and lifecycle** -- identity provider
- **Backup scheduling, offsite storage, and restoration testing** -- infrastructure
- **User privilege reviews** -- organizational IAM process
- **Microsoft Office macro policies** -- not applicable to Android
