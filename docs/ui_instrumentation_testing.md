# UI instrumentation and screenshot-oriented test coverage

This repository now includes deterministic Compose instrumentation tests for the app's key screens:

- destination search
- route preview
- active navigation UI
- settings
- debug overlay visibility

## Why this setup is CI-friendly

- Tests render screen content directly with fixed `AppUiState` fixtures instead of relying on live routing, geocoding, location, or network calls.
- A fake gradient map panel is injected during tests, so screenshots do not depend on MapLibre tile loading or internet access.
- Android test animations are disabled in Gradle to reduce flakiness.
- The tests assert key UI text/state and can optionally export screenshots for human review.

## Run locally

### Compile the instrumentation suite

```bash
gradle :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest
```

### Run the instrumentation suite on a connected emulator/device

```bash
gradle :app:connectedDebugAndroidTest
```

> Recommended device setup: use an emulator with animations disabled and leave the app in demo-mode-friendly defaults.

## Optional screenshot export

The instrumentation tests are screenshot-oriented: every key-flow test captures the rendered Compose node in-memory, and you can optionally persist PNG files while the tests run.

To save PNGs on the device/emulator instead of keeping them in-memory only, pass the instrumentation argument below:

```bash
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.recordScreenshots=true
```

When enabled, screenshots are written on the device/emulator under:

```text
/sdcard/Android/data/com.simonsaysgps/files/ui-test-snapshots/
```

You can copy them locally after the run with `adb pull` if needed.

## Artifact policy

- Generated screenshots are runtime artifacts only.
- They are **not** stored under source control.
- The repo `.gitignore` already excludes build outputs, and this PR does not add any committed PNGs or other binary screenshot baselines.
