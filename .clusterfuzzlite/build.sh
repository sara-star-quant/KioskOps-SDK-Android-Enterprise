#!/bin/bash -eu
# Build the PayloadCodec fuzz harness for ClusterFuzzLite.
#
# The fuzzed surface is pure JVM, so we compile it and the harness with kotlinc
# rather than running the Android Gradle build (which would need the Android SDK).
# -include-runtime bundles the Kotlin stdlib so the fuzzer jar is self-contained at
# runtime; the Jazzer API is only needed at compile time (the agent supplies it when
# the fuzzer runs).

SDK_SRC="$SRC/kioskops/kiosk-ops-sdk/src/main/java/com/sarastarquant/kioskops/sdk"
CFL="$SRC/kioskops/.clusterfuzzlite"

FUZZER_NAME=PayloadCodecFuzzer
FUZZER_CLASS=com.sarastarquant.kioskops.sdk.fuzz.PayloadCodecFuzzer
FUZZER_JAR="$OUT/${FUZZER_NAME}.jar"

kotlinc \
  "$CFL/stubs/RestrictTo.kt" \
  "$SDK_SRC/crypto/CryptoProvider.kt" \
  "$SDK_SRC/queue/PayloadCodec.kt" \
  "$CFL/fuzz/${FUZZER_NAME}.kt" \
  -classpath "$JAZZER_API_PATH" \
  -include-runtime \
  -d "$FUZZER_JAR"

# Execution wrapper. base-builder-jvm places jazzer_driver and
# jazzer_agent_deploy.jar in $OUT; reference them relative to the wrapper so the
# fuzzer is relocatable.
cat > "$OUT/$FUZZER_NAME" <<EOF
#!/bin/bash
# LLVMFuzzerTestOneInput
this_dir=\$(dirname "\$0")
LD_LIBRARY_PATH="\$JVM_LD_LIBRARY_PATH":\$this_dir \\
\$this_dir/jazzer_driver --agent_path=\$this_dir/jazzer_agent_deploy.jar \\
  --cp=\$this_dir/${FUZZER_NAME}.jar \\
  --target_class=${FUZZER_CLASS} \\
  --jvm_args="-Xmx2048m" \\
  "\$@"
EOF
chmod +x "$OUT/$FUZZER_NAME"
