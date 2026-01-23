# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.x.x   | :white_check_mark: |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

### How to Report

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please send an email to: **pzverkov@protonmail.com**

Include the following information:
- Type of vulnerability (e.g., injection, data exposure, authentication bypass)
- Full paths of affected source files
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact assessment and potential attack scenarios

### Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Resolution Target**: Within 90 days (depending on severity)

### What to Expect

1. Acknowledgment of your report within 48 hours
2. Regular updates on the progress of addressing the vulnerability
3. Credit in the security advisory (if desired) once the issue is resolved
4. Notification when the fix is released

### Scope

This security policy applies to:
- The KioskOps SDK library (`kiosk-ops-sdk` module)
- Official documentation and examples

Out of scope:
- Third-party dependencies (report to their maintainers)
- Sample applications used for demonstration purposes

## Security Best Practices

When using KioskOps SDK in production:

1. **Keep the SDK updated** to the latest version
2. **Enable all security defaults** (`SecurityPolicy.maximalistDefaults()`)
3. **Never disable PII filtering** without understanding the implications
4. **Use certificate pinning** for network sync endpoints
5. **Review audit trails** regularly for anomalies

## Security Features

KioskOps SDK includes enterprise security features:

- AES-256-GCM encryption at rest (Android Keystore backed)
- PII denylist filtering
- Tamper-evident audit chain (SHA-256)
- Payload size limits and queue pressure controls
- Optional HMAC request signing
