# Benchmarks

Microbenchmarks for the SDK's compute-bound hot paths, measured with
[androidx.benchmark](https://developer.android.com/studio/profile/benchmarking).
They run through the shipped SDK classes in the `:benchmark` module.

## Results

Measured on an emulator (`sdk_gphone64_arm64`, API 34, arm64). Emulator numbers
are directional, not representative of production hardware; treat physical-device
runs as authoritative. Reproduce with the command below and substitute your own
device's numbers.

| Operation | Median | Throughput | Allocations |
|-----------|--------|------------|-------------|
| `StatisticalAnomalyDetector.analyze` (per event) | 4.8 us | ~207,000 events/s | 47 |
| `FieldLevelEncryptor.encryptFields` (3 fields) | 46.1 us | ~21,700 ops/s | 492 |
| `FieldLevelEncryptor.decryptFields` (3 fields) | 39.2 us | ~25,500 ops/s | 444 |

Field encryption uses a software AES-256-GCM key so the number reflects the
cipher and JSON envelope work. The shipped `AesGcmKeystoreCryptoProvider` adds an
AndroidKeyStore lookup per call; that cost is device-specific (TEE/StrongBox) and
is not captured here.

## Reproduce

```bash
# Requires a connected device or emulator.
./gradlew :benchmark:connectedReleaseAndroidTest
```

Results are written to
`benchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected/<device>/*-benchmarkData.json`.

CI runs these weekly and on demand via the `Benchmark` workflow and uploads the
JSON as an artifact. The workflow does not gate pull requests.

## Scope

The benchmarks cover the per-event compute paths that run on the enqueue hot
path: anomaly scoring and field-level encryption. The audit hash chain is not
microbenchmarked: `PersistentAuditTrail` is Room-backed, so its cost is dominated
by disk IO rather than CPU, which makes it a poor microbenchmark target (each
iteration would fsync). Audit append/verify is exercised for correctness by the
instrumented and unit tests instead.
