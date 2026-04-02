# Australian Privacy Act 1988 -- KioskOps SDK Control Mapping

> **Disclaimer:** This document is an engineering reference only. It does not
> constitute a compliance certification, legal advice, or privacy assessment.
> The SDK has not been independently audited against the Australian Privacy Act
> 1988 or the Australian Privacy Principles (APPs). You must conduct your own
> assessment with qualified legal and privacy professionals. The APPs impose
> obligations on APP entities (organizations); an SDK provides technical
> controls that support but do not fulfill these obligations.

## Scope

The Australian Privacy Act 1988 establishes the Australian Privacy Principles
(APPs) governing the handling of personal information by APP entities. This
mapping covers the APPs where the SDK provides direct or supporting technical
controls. The `gdprDefaults()` config preset enables a privacy-focused
configuration that aligns with APP requirements.

---

## Australian Privacy Principles (APPs)

| APP | Principle | SDK Feature | Implementation | Notes/Limitations |
|-----|-----------|-------------|----------------|-------------------|
| APP 1.2 | Open and transparent management -- privacy policy | Data classification; PII detection | SDK tags data as PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED; PII detection identifies personal information in event payloads | Privacy policy authoring is organizational responsibility; SDK provides visibility into what data is collected |
| APP 1.3 | Open and transparent management -- practices and procedures | Audit trail; SBOM | Tamper-evident audit trail records all SDK data operations; CycloneDX SBOM documents SDK components | Documentation of organizational data handling practices is the entity's responsibility |
| APP 6.1 | Use or disclosure -- primary purpose | Data classification; retention controls | Data classification tags enforce purpose limitation; retention policies auto-delete data past its useful life; PII redaction removes unnecessary personal information | Purpose limitation enforcement is organizational/legal responsibility; SDK provides technical controls |
| APP 6.2 | Use or disclosure -- secondary purpose | PII redaction; field encryption | PII detection with REJECT action can block events containing unexpected personal information; field encryption restricts access to sensitive fields | Consent management and secondary purpose assessment is organizational responsibility |
| APP 8.1 | Cross-border disclosure | Local-first architecture; opt-in sync | SDK stores all data locally by default; network sync is disabled until explicitly enabled; host app controls data residency routing | Cross-border disclosure assessment and contractual safeguards are organizational responsibility; SDK ensures no data leaves device without explicit opt-in |
| APP 8.2 | Cross-border disclosure -- reasonable steps | Certificate pinning; mTLS; HMAC signing | Transport security protects data in transit when sync is enabled; certificate pinning prevents MITM during cross-border transfer | Reasonable steps to ensure overseas recipient compliance are organizational responsibility |
| APP 11.1 | Security -- reasonable steps to protect | AES-256-GCM encryption at rest; field encryption | All SDK data stores encrypted with Android Keystore-backed keys; field-level encryption for sensitive attributes; PII redaction minimizes stored personal information | SDK protects its own data stores; host app must secure data outside SDK scope |
| APP 11.2 | Security -- destruction or de-identification | Data deletion APIs; retention enforcement | `deleteUserData(userId)` erases per-user data; `wipeAllSdkData()` removes all SDK data; retention policies auto-purge expired data; PII redaction replaces values with `[REDACTED:TYPE]` markers | Covers SDK-local data only; host app and backend must handle their own data destruction |
| APP 12.1 | Access to personal information | Data export API | `exportUserData(userId)` exports queue events, audit events, and telemetry for a specific user as a ZIP archive (aligns with GDPR Art. 20 portability) | Export covers SDK-local data only; host app and backend data is out of scope |
| APP 12.3 | Access -- reasonable timeframes | Data export API | Export is a synchronous local operation; no network dependency for access requests | Response timeframe obligations are organizational responsibility |
| APP 13.1 | Correction of personal information | Data deletion API; PII redaction | `deleteUserData(userId)` enables removal of incorrect data; PII redaction can mask incorrect personal information | SDK does not support in-place record correction; delete-and-re-create is the recommended pattern |
| APP 13.2 | Correction -- notification to third parties | Audit trail | Audit trail records deletion operations for compliance evidence | Notification to third parties who received the data is organizational responsibility |

---

## Alignment with GDPR APIs

The SDK's GDPR-focused APIs (`exportUserData`, `deleteUserData`, `wipeAllSdkData`)
directly support Australian Privacy Act requirements:

| GDPR API | APP Alignment | Notes |
|----------|--------------|-------|
| `exportUserData(userId)` | APP 12 (Access) | ZIP export of user's SDK data |
| `deleteUserData(userId)` | APP 11.2 (Destruction), APP 13 (Correction) | Per-user data erasure |
| `wipeAllSdkData()` | APP 11.2 (Destruction) | Full SDK data removal |
| `PiiPolicy` with REDACT action | APP 6 (Use limitation), APP 11 (Security) | De-identification of personal information |
| `DataClassificationPolicy` | APP 1 (Transparency), APP 6 (Purpose limitation) | Visibility into data sensitivity |

---

## Controls outside SDK scope

The following APP obligations require organizational, legal, or infrastructure measures:

- **APP 2 (Anonymity and pseudonymity)** -- SDK provides pseudonymous deviceId; anonymity options are application-level
- **APP 3 (Collection -- solicited)** -- lawful basis and collection notices are organizational responsibility
- **APP 4 (Unsolicited personal information)** -- organizational policy
- **APP 5 (Notification of collection)** -- privacy notice authoring is organizational responsibility
- **APP 7 (Direct marketing)** -- SDK does not perform marketing; organizational responsibility
- **APP 9 (Adoption, use, or disclosure of government identifiers)** -- organizational/legal responsibility
- **APP 10 (Quality of personal information)** -- data quality is host-app responsibility
- **Notifiable Data Breaches (NDB) scheme** -- breach notification is organizational responsibility; SDK audit trail provides forensic evidence
