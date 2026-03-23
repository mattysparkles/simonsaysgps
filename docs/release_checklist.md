# Android release checklist

This checklist is for preparing **Simon Says GPS** for a Play Store submission or internal release candidate.

## Release blocker checklist

- [ ] Confirm the app builds a **release** variant successfully.
- [ ] Confirm no binary files were added in this release hardening pass.
- [ ] Confirm no secrets, private API keys, signing files, or generated artifacts are committed.
- [ ] Confirm release strings do not contain misleading **beta**, **coming soon**, debug-only, or placeholder copy on user-facing release surfaces.
- [ ] Confirm release settings hide unfinished provider scaffolding, soundtrack handoff controls, and heavy-vehicle placeholders.
- [ ] Confirm permission rationales are visible in the UI before or alongside the relevant action.
- [ ] Confirm the privacy policy and Play Console disclosures match the app's local-only visit history and user-initiated microphone behavior.

## Required configuration

### Build-time config

- `OSRM_BASE_URL`
  - Should point to a stable HTTPS routing endpoint.
  - If omitted or malformed, the app now falls back to the public OSRM endpoint instead of crashing at startup.
- `NOMINATIM_BASE_URL`
  - Should point to a stable HTTPS geocoding endpoint.
  - If omitted or malformed, the app now falls back to the public Nominatim endpoint.
- `GRAPH_HOPPER_BASE_URL`
  - Optional for release if GraphHopper is not exposed.
  - If malformed, the app falls back to GraphHopper's public HTTPS base URL.
- `GRAPH_HOPPER_API_KEY`
  - Required only if the release should expose GraphHopper as a selectable provider.
  - Leave blank if GraphHopper should stay hidden from the release-safe surface.
- `MAP_STYLE_URL`
  - Optional override for production map styling.
  - If blank or invalid, the app uses a safe fallback style URL instead of crashing.
- `MAP_STYLE_FALLBACK_URL`
  - Keep as a valid HTTPS style URL if overridden.
  - If invalid, the app now falls back to the built-in safe style URL.
- `ALLOW_HTTP_MAP_STYLE_URL`
  - Keep `false` for release.
  - Only enable for local development hosts.

### Permissions and declarations to verify

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
  - Used only for active trip positioning, reroute timing, and route progress.
  - No background location permission is declared.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`
  - Required for active navigation while the trip is running.
- `POST_NOTIFICATIONS`
  - Requested separately from location on Android 13+.
  - Users see a rationale on the route preview screen before the prompt.
- `RECORD_AUDIO`
  - Used only for user-started voice capture from the voice assistant flow.
  - Typed fallback remains available if denied.
- Optional hardware features are marked `required="false"` so Play Store device filtering is less aggressive.

## What should remain disabled or gated for release

- Debug overlay controls.
- Demo mode.
- Valhalla/provider placeholder routing picks.
- Soundtrack/music-provider scaffolding.
- Heavy-vehicle size fields that imply unsupported route enforcement.
- Any moderation/reporting hook that is not complete enough for real user expectations.

## Manual smoke test flows

Run these on at least one Android 13+ device and one pre-Android-13 device when practical.

### 1. Startup and onboarding

1. Install the release candidate fresh.
2. Launch the app.
3. Verify onboarding copy explains the Simon Says mechanic clearly.
4. Verify the app reaches the map screen even if optional API config overrides are blank or malformed.

### 2. Location permission flow

1. Start from a fresh install with location denied.
2. Verify the map screen explains why location is needed.
3. Deny location and confirm search/browsing still works.
4. Grant location and confirm current position starts updating without a restart.

### 3. Route preview and navigation start

1. Select a destination and preview a route.
2. Verify the route preview explains foreground-service behavior honestly.
3. On Android 13+, tap the notification-permission action and confirm the permission prompt appears.
4. Start navigation.
5. Verify the ongoing navigation notification appears and the service stops when navigation ends or arrival is detected.

### 4. Voice permission flow

1. Open Voice Assistant with microphone denied.
2. Verify the screen explains that typed commands still work.
3. Tap the microphone button and confirm the UI asks for permission rather than pretending to listen.
4. Grant microphone access and verify one-shot capture can start.

### 5. Explore entry and privacy controls

1. Open Explore from the bottom navigation.
2. Open Explore Settings.
3. Verify visit history wording says the data is local-only on-device.
4. Toggle visit history off and confirm the disable path is clear.
5. Add or seed a visit, then remove one item and clear all history.

### 6. Settings sanity pass

1. Open Settings in a release build.
2. Confirm no debug/developer-only controls appear.
3. Confirm voice/privacy copy is clear and does not imply hidden background listening.
4. Confirm unfinished features are described as unavailable, not as faux-complete features.

## Known limitations to reflect in store listing or privacy policy

- Navigation relies on network-backed routing/geocoding unless a recent cached result is available.
- Visit history is local-only in this build and is not synced across devices.
- Voice capture is user-initiated only; there is no hidden always-listening mode.
- Music-provider handoff is intentionally unavailable in the release-safe surface.
- Large-vehicle restriction enforcement is not promised in the release-safe surface.
- Explore provider coverage is partial and some categories may rely on local/curated signals rather than a full commercial POI backend.

## Notes for Play Console disclosures

- **Location:** disclose active trip/navigation use.
- **Microphone:** disclose user-started voice command capture only.
- **Notifications:** disclose trip-status notification usage on Android 13+.
- **Data safety / privacy policy:** describe visit history and review drafts as local-first/on-device for this build if that remains accurate.

## Binary-files note

No binary files were added as part of this release-readiness hardening pass.
