# License and patent audit

Engineering record, not legal advice. Date: 2026-11-19. Scope: main at 70f8e61.

## Project license

- BSL 1.1 on every tag checked (v0.1.0 and v1.6.0). Licensor: SARA STAR QUANT LLC. Change date per LICENSE.
- NOTICE and THIRD_PARTY_NOTICES.md at the repository root cover the runtime classpath.

## Dependencies (runtime)

- sqlcipher-android (Zetetic, BSD-style) is androidTest-only and is not shipped.
- 70 runtime artifacts: 69 Apache-2.0, 1 BSD-3-Clause (datastore protobuf). No copyleft on the runtime classpath. Copyleft (LGPL jna, trove4j, EPL junit) appears only in the full SBOM through build and test tooling.
- androidx, okhttp, kotlinx: Apache-2.0. Apache-2.0 includes an express patent grant from each contributor.
- Test-only (not shipped): junit, truth, robolectric, jazzer, classgraph.
- Not done: diff the runtime classpath across earlier tags.

## Cryptographic and protocol surface

The SDK calls the platform JCA and Android Keystore. It does not ship its own primitive implementations.

| Used | Reference | IETF IPR disclosures |
|---|---|---|
| HKDF | RFC 5869 | 0 |
| Merkle-style hash chain, checkpoints | cites RFC 6962 | 0 (RFC 9162 also 0) |
| PBKDF2 | RFC 8018, RFC 6070 vectors | 0 |
| HMAC-SHA256 | RFC 2104 | 0 |
| AES-GCM | RFC 5116 | 0 |
| ECDSA (SHA256withECDSA) | RFC 6979, RFC 8032 checked | 0 |

Control: RFC 6386 returns 2 disclosures, so the zero counts are meaningful. RFC 3161 returns 4, but the SDK does not use RFC 3161 timestamping. Keep it that way, or review disclosures 461, 483, 2224 and 4464 before adding it.

## Rules that keep risk low

- Stay with standard constructions (HKDF, AES-GCM, ECDSA P-256, SHA-256). Do not add novel or academic constructions without a patent check.
- Keep primitives in the platform provider, not in our code.
- Do not add RFC 3161 timestamping, blockchain anchoring or other patent-dense features without review.

## Not covered

- No USPTO, EPO or Google Patents claim search. IETF disclosures cover only what vendors declared to the IETF.
- No review of the broader offline-queue, fleet-management or compliance-reporting feature areas, where non-practicing entities usually file.
- No scanner run (ScanCode) and no AI-generated-code resemblance check.
- No trademark search. Confirm the name in the USPTO, EUIPO and WIPO databases.
- Patent trolls (non-practicing entities) can assert any patent regardless of the IETF record. The practical defenses are Apache-2.0 dependency patent grants, prior-art documentation, and a defensive publication or insurance decision for the owner.
