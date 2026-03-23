# Binary Files Manifest

This repository intentionally excludes tracked binary artifacts from the pull request so the PR remains text-only.

## Removed binary files

| Path | Type | Size (bytes) | SHA-256 | Purpose | How to recreate/upload later |
| --- | --- | ---: | --- | --- | --- |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper JAR | 43764 | `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172` | Required by `./gradlew` to bootstrap Gradle without a system install. | Run `gradle wrapper --gradle-version 8.14.3` from the repo root, or upload the matching wrapper JAR generated from the same Gradle version. |

## Notes

- `gradle/wrapper/gradle-wrapper.properties` is still committed, so the expected wrapper distribution/version is preserved.
- In this environment, the wrapper JAR was generated locally from Gradle 8.14.3 before being removed from the tracked tree.

- No new binary files were added by the visit history, personalized Explore, home anchor, or transport-profile PR.
- No new binary files were added by the Place Detail, internal reviews, or See Reviews / Leave Review flow PR.
- No new binary files were added by the saved places / favorite persistence and reuse PR.

- No new binary files were added by the voice assistant v1 persistence and native speech-recognition PR.
- No new binary files were added by the Android release-readiness hardening pass.

- No new binary files were added by the release-safe gating, onboarding, and launch-polish pass.
