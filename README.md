# Simon Says GPS

Simon Says GPS is a turn-by-turn Android navigation app with a Simon Says rules engine layered on top of real routing. It behaves like a normal OSM-based GPS first, but only turns explicitly authorized with **"Simon says"** count as correct in game logic.

## What the app does

- Search for destinations with debounced Nominatim queries, short-lived cached results, and recent destination shortcuts.
- Preview an OSM route on an OpenStreetMap/MapLibre map, with lightweight cached route previews for brief reconnect scenarios.
- Start navigation with spoken prompts powered by Android TextToSpeech.
- Pick from multiple prompt personalities for spoken and on-screen navigation callouts.
- Automatically start and stop the foreground navigation service with the active turn-by-turn session.
- Detect upcoming maneuvers and decide whether a turn was authorized, missed, or unauthorized.
- Reroute with playful prompts like: _"Oh, Simon didn't say. Rerouting."_
- Offer a debug overlay, demo mode, and a routing-provider selector for emulator testing and provider experiments.

## Stack choices

- **UI:** Kotlin + Jetpack Compose + Navigation Compose.
- **Architecture:** MVVM with clear domain/data/UI separation.
- **DI:** Hilt.
- **Map rendering:** MapLibre Android SDK with an OSM-friendly public style URL placeholder.
- **Routing:** Provider-selectable routing through a swappable `RoutingRepository` abstraction with provider adapters.
- **Geocoding:** Nominatim through a swappable `GeocodingRepository` abstraction.
- **Persistence:** DataStore for settings, lightweight search/route caches, and a persisted active-navigation snapshot for safe session restoration.
- **Background resilience:** WorkManager coordinates deferred session recovery checks, while the foreground service remains responsible for real-time navigation.
- **Prompt personalities:** Data-driven prompt profiles, making it easy to add new Simon tones later.
- **Location:** Fused Location Provider, plus a demo simulator for emulator testing.

## Project structure

- `app/src/main/java/com/simonsaysgps/data`: network, repositories, routing provider adapters, and location providers.
- `app/src/main/java/com/simonsaysgps/domain`: models, routing/game engine, provider-selection abstractions, and utilities.
- `app/src/main/java/com/simonsaysgps/ui`: Compose screens, components, navigation, and view models.
- `app/src/test/java/com/simonsaysgps`: unit tests for Simon Says behavior and routing abstractions.

## Setup

### Requirements

- Android Studio with Android SDK 36 installed.
- JDK 17+.
- Internet access for Nominatim, routing provider APIs, and map tiles. Recent search results and the latest matching route preview can be reused briefly when connectivity drops, but fresh searches and new routes still require network access.

### Run locally

1. Open the repo in Android Studio.
2. Sync Gradle.
3. Run the `app` configuration on a device or emulator.
4. Grant location permission.
5. For emulator-first testing, leave **Demo mode** enabled in Settings.
6. Choose **Prompt personality** in Settings to switch between Classic Simon, Snarky Simon, and Polite Simon. The selected tone is persisted in DataStore and reused by TextToSpeech prompts.
7. Choose **Routing provider** in Settings if you want to test a non-default provider.

> Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally excluded from the tracked PR. See `docs/BINARY_FILES_MANIFEST.md` for its checksum and regeneration instructions.

### Routing provider configuration

The routing layer now separates provider selection from provider implementation:

- `RoutingRepository` remains the app-facing abstraction used by the view model and domain flow.
- `ProviderRoutingRepository` adapters register concrete providers such as OSRM and GraphHopper.
- `SelectingRoutingRepository` reads the persisted user setting and resolves the active provider, falling back to the configured default provider when the requested provider is unsupported or unconfigured.

Default/provider-specific properties live in `gradle.properties` and can be overridden with environment-specific Gradle properties:

- `DEFAULT_ROUTING_PROVIDER=OSRM`
- `OSRM_BASE_URL=https://router.project-osrm.org/`
- `GRAPH_HOPPER_BASE_URL=https://graphhopper.com/api/1/`
- `GRAPH_HOPPER_API_KEY=`
- `GRAPH_HOPPER_PROFILE=car`
- `VALHALLA_BASE_URL=https://valhalla1.openstreetmap.de/`
- `NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org/`
- `MAP_STYLE_URL=https://demotiles.maplibre.org/style.json`

#### Supported providers

- **OSRM:** Fully implemented and remains the default provider.
- **GraphHopper:** Adapter implemented for route calculation and maneuver mapping. It is only considered available when `GRAPH_HOPPER_API_KEY` is configured.
- **Valhalla:** Selection option and configuration placeholder are present, but a concrete adapter is not implemented in this PR. Selecting it falls back gracefully to the configured default provider when available.

### API/base URL configuration

The app builds with placeholder/default public endpoints via `gradle.properties`. For a more production-minded deployment, point these to your own hosted OSRM/Nominatim/tiles infrastructure or a paid routing provider. GraphHopper additionally requires a valid API key.

## Simon Says rules

### Basic mode

Every maneuver is marked `REQUIRED_SIMON_SAYS`. If the user turns and Simon authorized it, the route continues. If not, the app reroutes.

### Mischief mode

Alternating maneuvers are flagged `NORMAL_INFO_ONLY`, creating fake-out turns. If the route geometry suggests a turn and the driver follows a non-authorized maneuver, the app calls that out as a Simon violation and reroutes.

## Testing the navigation logic

- Enable **Demo mode** to use a fake location stream in the emulator.
- Switch **Prompt personality** in Settings to preview different tones without changing navigation rules.
- Switch **Routing provider** in Settings to validate provider resolution and fallback behavior.
- Enable **Debug overlay** to inspect GPS coordinates, step index, next maneuver distance, authorization state, heading, and last reroute reason. Search and route status messages also call out when cached data is being used because a request timed out or the device is offline.
- Unit tests cover prompt generation, authorization assignment, unauthorized turn detection, missed turn logic, provider selection/fallback behavior, routing repository mapping behavior, search debouncing, recent destination persistence/mapping behavior, and navigation-session restoration/storage behavior.

## Navigation lifecycle resilience

- Active turn-by-turn sessions are still driven by the foreground service path; WorkManager is **not** used for the per-location navigation loop.
- While a navigation session is active, the app persists a restorable snapshot of the current route/session state in DataStore.
- When the app process is recreated or the user reopens the app, the last active session is restored into UI state so the active navigation screen can recover without recomputing the route first.
- WorkManager schedules a one-time recovery pass plus a unique periodic safety-net check for long-running trips. Those jobs only verify whether an active persisted session should reassert the foreground service after backgrounding or process recreation.
- When navigation ends, the persisted session snapshot and recovery work are cleared so stale sessions are not revived later.

## Search/routing cache behavior

- Destination search results are cached per normalized query for a short window so repeated searches can reuse recent results without immediately hitting Nominatim again.
- Route previews are cached for the latest matching origin/destination pairs using a coarse coordinate key, which helps when a request is retried shortly after a timeout or temporary disconnect.
- Network requests now use explicit connect/read/call timeouts and a small retry policy for transient GET failures and 5xx responses.
- User-facing messages distinguish between no network, timeout/server failure, and empty search results. Provider-selection fallback happens at repository resolution time so the app stays stable even when a requested provider is not yet implemented.
- This is not full offline navigation: map tiles, fresh routing, and uncached searches still require network access.

## Known limitations

- Public OSRM and Nominatim endpoints are convenient defaults but are not sufficient for real production scale.
- GraphHopper support depends on a configured API key and currently uses a lightweight maneuver mapping based on the Directions API instruction sign values.
- Valhalla is scaffolded for selection/configuration but not yet implemented as a concrete routing adapter.
- Offline support in this phase is intentionally lightweight: repeated destination queries and the latest matching route preview are cached, but the app does not perform full offline routing.
- Turn detection heuristics currently use route proximity, bearing deltas, and step proximity; they are intentionally understandable rather than fully map-matched.
- The initial implementation keeps most functionality in one Android app module for repo simplicity.
- Map overlay styling is intentionally lightweight for phase 1.
- The foreground navigation service now starts automatically when active guidance begins and stops automatically when guidance ends, arrives, or is cancelled.
- Active navigation sessions are now snapshotted to DataStore and can be restored after app reopen or process recreation, with WorkManager providing deferred recovery checks for long-running trips.

## Phase 1 delivered

- Destination search with debounced lookups and recent destination storage.
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
- Expand the partial Valhalla scaffold into a concrete adapter.
- Add screenshot-based UI tests and instrumentation tests.
- Replace demo style URL with a self-hosted production tile/style stack.

## TODOs

- Tighten missed-turn detection to avoid jitter-driven reroutes.
- Expand lightweight cache coverage and retry/backoff policies as the app grows.
