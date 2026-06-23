FROM gcr.io/oss-fuzz-base/base-builder-jvm

# Standalone Kotlin compiler. The SDK is an Android (com.android.library) module,
# so its Gradle build needs the Android SDK, which base-builder-jvm does not ship.
# The fuzzed surface (PayloadCodec + CryptoProvider) is pure JVM, so the harness
# compiles those sources directly with kotlinc instead of running the Android build.
ENV KOTLIN_VERSION=2.1.0
RUN curl -fsSL -o /tmp/kotlin.zip \
      https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip && \
    unzip -q /tmp/kotlin.zip -d /opt && \
    rm /tmp/kotlin.zip
ENV PATH="/opt/kotlinc/bin:${PATH}"

COPY . $SRC/kioskops
COPY .clusterfuzzlite/build.sh $SRC/build.sh
WORKDIR $SRC/kioskops
