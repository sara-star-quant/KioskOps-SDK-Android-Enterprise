package androidx.annotation

// Compile-only stub for the RestrictTo annotation. The fuzz harness compiles the
// few pure-JVM SDK sources it targets (PayloadCodec, CryptoProvider) directly with
// kotlinc, outside the Android build, so the real androidx.annotation artifact is
// not on the classpath. RestrictTo has BINARY retention and no runtime effect, so a
// minimal stub is sufficient to satisfy the compiler.
@Retention(AnnotationRetention.BINARY)
annotation class RestrictTo(vararg val value: Scope) {
  enum class Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, GROUP_ID, TESTS, SUBCLASSES }
}
