# Simon Says GPS

Simon Says GPS is a turn-by-turn Android navigation app with a Simon Says rules engine layered on top of real routing. It behaves like a normal OSM-based GPS first, but only turns explicitly authorized with **"Simon says"** count as correct in game logic.

## What the app does

- Search for destinations with Nominatim.
- Preview an OSM route on an OpenStreetMap/MapLibre map.
- Start navigation with spoken prompts powered by Android TextToSpeech.
- Automatically start and stop the foreground navigation service with the active turn-by-turn session.
- Detect upcoming maneuvers and decide whether a turn was authorized, missed, or unauthorized.
- Reroute with playful prompts like: _"Oh, Simon didn't say. Rerouting."_
- Offer a debug overlay and demo mode for emulator testing.

## Stack choices

- **UI:** Kotlin + Jetpack Compose + Navigation Compose.
- **Architecture:** MVVM with clear domain/data/UI separation.
- **DI:** Hilt.
- **Map rendering:** MapLibre Android SDK with an OSM-friendly public style URL placeholder.
- **Routing:** OSRM HTTP API through a swappable `RoutingRepository` abstraction.
- **Geocoding:** Nominatim through a swappable `GeocodingRepository` abstraction.
- **Persistence:** DataStore for settings.
- **Location:** Fused Location Provider, plus a demo simulator for emulator testing.

## Project structure

- `app/src/main/java/com/simonsaysgps/data`: network, repositories, and location providers.
- `app/src/main/java/com/simonsaysgps/domain`: models, routing/game engine, prompt generation, and utilities.
- `app/src/main/java/com/simonsaysgps/ui`: Compose screens, components, navigation, and view models.
- `app/src/test/java/com/simonsaysgps`: unit tests for Simon Says behavior and routing abstractions.

## Setup

### Requirements

- Android Studio with Android SDK 36 installed.
- JDK 17+.
- Internet access for Nominatim, OSRM, and map tiles.

### Run locally

1. Open the repo in Android Studio.
2. Sync Gradle.
3. Run the `app` configuration on a device or emulator.
4. Grant location permission.
5. For emulator-first testing, leave **Demo mode** enabled in Settings.

> Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally excluded from the tracked PR. See `docs/BINARY_FILES_MANIFEST.md` for its checksum and regeneration instructions.

### API/base URL configuration

The app builds with placeholder/default public endpoints via `gradle.properties`:

- `OSRM_BASE_URL=https://router.project-osrm.org/`
- `NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org/`
- `MAP_STYLE_URL=https://demotiles.maplibre.org/style.json`
- `GRAPH_HOPPER_API_KEY=` (reserved placeholder for future provider swap)

For a more production-minded deployment, point these to your own hosted OSRM/Nominatim/tiles infrastructure or a paid routing provider.

## Simon Says rules

### Basic mode

Every maneuver is marked `REQUIRED_SIMON_SAYS`. If the user turns and Simon authorized it, the route continues. If not, the app reroutes.

### Mischief mode

Alternating maneuvers are flagged `NORMAL_INFO_ONLY`, creating fake-out turns. If the route geometry suggests a turn and the driver follows a non-authorized maneuver, the app calls that out as a Simon violation and reroutes.

## Testing the navigation logic

- Enable **Demo mode** to use a fake location stream in the emulator.
- Enable **Debug overlay** to inspect GPS coordinates, step index, next maneuver distance, authorization state, heading, and last reroute reason.
- Unit tests cover prompt generation, authorization assignment, unauthorized turn detection, missed turn logic, and routing repository mapping behavior.

## Known limitations

- Public OSRM and Nominatim endpoints are convenient defaults but are not sufficient for real production scale.
- Turn detection heuristics currently use route proximity, bearing deltas, and step proximity; they are intentionally understandable rather than fully map-matched.
- The initial implementation keeps most functionality in one Android app module for repo simplicity.
- Map overlay styling is intentionally lightweight for phase 1.
- The foreground navigation service now starts automatically when active guidance begins and stops automatically when guidance ends, arrives, or is cancelled.

## Phase 1 delivered

- Destination search.
- Map preview.
- Route calculation.
- Active navigation UI.
- Voice prompt generation.
- Simon Says engine with reroute triggers.
- Settings + debug overlay.
- Demo simulator.
- Unit tests.

## Future improvements

- Add a dedicated `navigation` module and split provider integrations into separate Gradle modules.
- Improve route snapping and off-route heuristics with better polyline projection and hysteresis.
- Add lane guidance, arrival state, and richer maneuver banners.
- Support provider selection between OSRM, GraphHopper, and Valhalla.
- Introduce WorkManager/service orchestration for robust long-running navigation sessions.
- Add screenshot-based UI tests and instrumentation tests.
- Replace demo style URL with a self-hosted production tile/style stack.

## TODOs

- Add user-selectable prompt personalities.
- Add search result debouncing and recent destinations.
- Tighten missed-turn detection to avoid jitter-driven reroutes.
- Add offline caches and resilient networking.
