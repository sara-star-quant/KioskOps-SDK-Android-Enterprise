# KioskOps SDK - Go-To-Market (One Page)

Related: [README](README.md) | [Roadmap](ROADMAP.md) | [Features](docs/FEATURES.md) | [Security and Compliance](docs/SECURITY_COMPLIANCE.md)

## Product
Enterprise Android SDK for offline-first kiosk, retail, logistics, and field-service apps. Encrypted event queue, tamper-evident audit trail, compliance mapping (FedRAMP/NIST/GDPR). Embedded by app teams, not sold as hardware.

## Positioning
One sharp claim, not a security platform: "Your devices lose network and still log every event, encrypted and provably untampered, ready for audit." Avoid head-on fight with Samsung Knox and Zebra on generic device security.

## Beachhead
Pick one vertical where regulated + offline + rugged overlap:
- Primary: field service (utilities, medical-device techs, gov contractors). NIST/FedRAMP pull, real offline need.
- Defer generic retail kiosk. OEM/MDM bundles strongest and willingness-to-pay lowest there.

## Buyer
Not the OEM. The app/platform team running the Android fleet: VP Eng, Head of Store/Field Systems, compliance lead. Economic buyer is whoever owns audit risk.

## Model: open-core
- Free tier (MIT, public GitHub): encryption + local audit log. Drives developer adoption, zero sales friction.
- Paid tier: compliance mappings (NIST 800-53 control coverage), audit-export tooling, signed attestation, SLA support. Compliance buyers pay for the paperwork and the support, not the crypto.
- Attach: filesigner-sdk-android (hardware-backed signing) as a paid bundle module.

## Pricing
$12-30 per device per year, blended ~$18. Annual contracts with a minimum device commit. Floor deal ~$15-50K/yr. Expand by fleet size (land-and-expand).

## Year-1 plan
1. Ship free tier on GitHub, write quickstart, get embeds.
2. Land 2-3 reference customers in the beachhead vertical. Logo + "passed audit" case study is the unlock.
3. Publish one NIST 800-53 control-coverage mapping as a sales artifact.
4. Convert free adopters to paid via the compliance/support tier.

## Distribution and credibility
Quantum-Go (PQ crypto, FIPS 140-3 mode) is the halo: it makes the org credible to security buyers. Use it in the pitch deck, not as a product line.

## Success metric / kill gate
Validate that at least one customer pays for the compliance/support tier within ~6 months. If the free tier gets adoption but nobody pays for the paperwork, there is no business. Stop before building more.

## Top risks
- Build-vs-buy: Knox, Zebra, Android Enterprise, and MDM vendors ship overlapping encryption/attestation free. Differentiate on offline audit + compliance evidence, not raw crypto.
- Long enterprise sales cycles: sub-1% market capture is realistic in years 1-3. Plan runway accordingly.
- Compliance moat is narrow: FedRAMP/NIST is the wedge into gov/regulated buyers but shrinks the addressable market; GDPR is table-stakes.