# Contributing

## Development Setup

### Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug or later |
| JDK | 17+ |
| Gradle | 8.11+ (via wrapper) |

### Clone and Build

```bash
git clone https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise.git
cd KioskOps-SDK-Android-Enterprise
./gradlew build
```

## Common Tasks

| Task | Command |
|------|---------|
| Build SDK | `./gradlew :kiosk-ops-sdk:assembleRelease` |
| Run unit tests | `./gradlew testDebugUnitTest` |
| Run fuzz tests | `./gradlew :kiosk-ops-sdk:testDebugUnitTest --tests "*FuzzTest*"` |
| Run lint | `./gradlew lintDebug` |
| Run all checks | `./gradlew check` |

## Testing

### Unit Tests

Unit tests run on the JVM using Robolectric:

```bash
./gradlew testDebugUnitTest
```

Test reports: `kiosk-ops-sdk/build/reports/tests/`

### Fuzz Tests

Fuzz tests use [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) to find crashes and edge cases:

```bash
./gradlew :kiosk-ops-sdk:testDebugUnitTest --tests "*FuzzTest*"
```

Fuzz targets:
- `PayloadCodecFuzzTest` - Tests encode/decode with random inputs

### Lint

```bash
./gradlew lintDebug
```

Report: `kiosk-ops-sdk/build/reports/lint-results-debug.html`

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` formatting (auto-applied on build)
- Keep functions small and focused
- Document public APIs with KDoc

## Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run tests and lint (`./gradlew check`)
5. Commit with a descriptive message
6. Push to your fork
7. Open a Pull Request

### PR Requirements

- All CI checks must pass
- Unit test coverage for new code
- No new lint warnings
- Update documentation if needed

## Reporting Issues

Use [GitHub Issues](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/issues) for:
- Bug reports
- Feature requests
- Documentation improvements

For security vulnerabilities, see [SECURITY.md](.github/SECURITY.md).
