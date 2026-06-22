# Compliance control traceability

Generated from `@ComplianceControl` annotations by `ComplianceTraceabilityTest`.
Do not edit by hand; run `./gradlew :kiosk-ops-sdk:testDebugUnitTest -Pcompliance.update` to regenerate.

| Framework | Control | Verified by |
|-----------|---------|-------------|
| NIST SP 800-171 | 3.13.11 | AesGcmKatTest; FipsComplianceCheckerTest.check returns result with provider info; KioskOpsConfigTest.cuiDefaults enforces NIST SP 800-171 controls; Pbkdf2KatTest |
| NIST SP 800-171 | 3.3.1 | KioskOpsConfigTest.cuiDefaults enforces NIST SP 800-171 controls; RetentionEnforcerTest.maximalist defaults include 365 day minimum audit retention |
