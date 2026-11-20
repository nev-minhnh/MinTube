# Baseline (pristine Litube 4.0.0, commit 5fee29b)

Run on 2026-11-20, Windows 11, Temurin JDK 21.0.13, Gradle 8.11.1 (wrapper), Android SDK
platform 36 / build-tools 36.0.0. Command, from a clean checkout:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue
```

Result: `BUILD SUCCESSFUL in 13m 53s`, 56 tasks executed.

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 243 suites, **1787 tests, 0 failures, 0 errors, 2 skipped** |
| `:app:lintDebug` | **0 errors**, 592 warnings |
| `:app:assembleDebug` | `app-debug.apk` built |
| `:app:assembleDebugAndroidTest` (separate run, 1m 55s) | `app-debug-androidTest.apk` built |

These numbers are the floor for every later phase: the passing unit-test count must not drop
below 1785 passed (1787 run), and lint must stay at 0 errors.

Not part of this baseline (not yet run locally): `assembleRelease`, `lintVitalRelease`,
the `minifiedDebug` builds, and the instrumented/emulator suites.
