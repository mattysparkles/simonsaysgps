# Simon Says GPS

Simon Says GPS is a turn-by-turn Android navigation app with a Simon Says rules engine layered on top of real routing. It behaves like a normal OSM-based GPS first, but only turns explicitly authorized with **"Simon says"** count as correct in game logic.

## What the app does

- Search for destinations with debounced Nominatim queries, short-lived cached results, and recent destination shortcuts.
- Preview an OSM route on an OpenStreetMap/MapLibre map, with lightweight cached route previews for brief reconnect scenarios.
- Start navigation with spoken prompts powered by Android TextToSpeech.
- Follow richer active-navigation banners with clearer Simon Says authorization messaging, arrival handling, and lane-guidance-ready placeholders.
- Pick from multiple prompt personalities for spoken and on-screen navigation callouts.
- Automatically start and stop the foreground navigation service with the active turn-by-turn session.
- Detect upcoming maneuvers and decide whether a turn was authorized, missed, or unauthorized.
- Reroute with playful prompts like: _"Oh, Simon didn't say. Rerouting."_
- Offer a debug overlay, demo mode, and a routing-provider selector for emulator testing and provider experiments.
- Add an Explore tab foundation with intent chips, ranked results, explainable suggestion reasons, configurable safety/detour rules, and demo providers that exercise the existing location/map stack.
- Personalize Explore with first-party visit history, home anchors, Close to Home and On My Way ranking, plus transport/route-style preferences that are surfaced honestly when provider support is partial.

## Stack choices

- **UI:** Kotlin + Jetpack Compose + Navigation Compose.
- **Architecture:** MVVM with clear domain/data/UI separation.
- **DI:** Hilt.
- **Map rendering:** MapLibre Android SDK with environment-driven style configuration, a safe development fallback, and production/self-hosted deployment scaffolding.
- **Routing:** Provider-selectable routing through a swappable `RoutingRepository` abstraction with provider adapters.
- **Geocoding:** Nominatim through a swappable `GeocodingRepository` abstraction.
- **Persistence:** DataStore for settings, Explore preferences, lightweight search/route caches, first-party visit history, and a persisted active-navigation snapshot for safe session restoration.
- **Explore discovery:** A dedicated Explore domain stack with provider abstractions for place data, event data, visit history, reviews, promotions, plus an isolated ranking engine.
- **Background resilience:** WorkManager coordinates deferred session recovery checks, while the foreground service remains responsible for real-time navigation.
- **Prompt personalities:** Data-driven prompt profiles, making it easy to add new Simon tones later.
- **Location:** Fused Location Provider, plus a demo simulator for emulator testing.

## Project structure

The codebase is now split into a small set of Gradle modules so navigation logic and provider integrations can evolve independently without turning the app module into a catch-all:

- `:app`: Android entry point, Compose UI, Hilt wiring, location implementations, DataStore persistence, foreground service orchestration, and instrumentation tests.
- `:navigation`: shared navigation/domain code including models, Simon Says engine, prompt generation, repository contracts, provider-selection logic, and navigation use cases.
- `:providers`: network-facing provider integrations including Nominatim geocoding, OSRM routing, GraphHopper routing, Retrofit APIs, and provider networking helpers.

Source roots now map cleanly onto those boundaries:

- `app/src/main/java/com/simonsaysgps`: app wiring/UI plus Android-specific infrastructure.
- `navigation/src/main/java/com/simonsaysgps/domain`: reusable navigation/domain engine and contracts.
- `navigation/src/main/java/com/simonsaysgps/data/repository`: provider-selection configuration and routing-repository delegation.
- `providers/src/main/java/com/simonsaysgps/data`: provider adapters plus remote API DTOs.
- `navigation/src/test`, `providers/src/test`, and `app/src/test`: unit tests aligned with each module's responsibility.
- `app/src/androidTest/java/com/simonsaysgps`: deterministic Compose instrumentation coverage for key UI flows and optional screenshot export.

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

Default/provider-specific properties live in `gradle.properties` and can be overridden with environment-specific Gradle properties or environment variables of the same name:

- `DEFAULT_ROUTING_PROVIDER=OSRM`
- `OSRM_BASE_URL=https://router.project-osrm.org/`
- `GRAPH_HOPPER_BASE_URL=https://graphhopper.com/api/1/`
- `GRAPH_HOPPER_API_KEY=`
- `GRAPH_HOPPER_PROFILE=car`
- `VALHALLA_BASE_URL=https://valhalla1.openstreetmap.de/`
- `NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org/`
- `MAP_STYLE_URL=`
- `MAP_STYLE_FALLBACK_URL=https://demotiles.maplibre.org/style.json`
- `ALLOW_HTTP_MAP_STYLE_URL=false`

#### Supported providers

- **OSRM:** Fully implemented and remains the default provider.
- **GraphHopper:** Adapter implemented for route calculation and maneuver mapping. It is only considered available when `GRAPH_HOPPER_API_KEY` is configured.
- **Valhalla:** Selection option and configuration placeholder are present, but a concrete adapter is not implemented in this PR. Selecting it falls back gracefully to the configured default provider when available.

### API/base URL configuration

The app builds with placeholder/default public endpoints via `gradle.properties`. For a more production-minded deployment, point these to your own hosted OSRM/Nominatim/tiles infrastructure or a paid routing provider. GraphHopper additionally requires a valid API key.

### Map style configuration

The app no longer assumes a single demo map style URL. Instead, the map style is resolved from a small configuration model intended to work for local development, CI, and production:

1. `MAP_STYLE_URL` is the primary environment-driven MapLibre style JSON URL.
2. `MAP_STYLE_FALLBACK_URL` is a safe fallback used when `MAP_STYLE_URL` is blank or invalid. The repo defaults this to the public MapLibre demo style so the app still renders out of the box.
3. `ALLOW_HTTP_MAP_STYLE_URL` defaults to `false` so production-style misconfiguration does not silently point the app at insecure remote HTTP endpoints.

Resolution rules:

- Gradle property values win first.
- Environment variables with the same names are supported next, which makes CI/deployment configuration straightforward.
- If `MAP_STYLE_URL` is blank, malformed, or uses insecure remote HTTP when `ALLOW_HTTP_MAP_STYLE_URL=false`, the app logs a warning, shows a small in-app fallback notice over the map, and uses `MAP_STYLE_FALLBACK_URL` instead.
- Local development hosts such as `localhost`, `127.0.0.1`, and Android emulator loopback `10.0.2.2` are allowed over HTTP without flipping the global insecure flag.

Recommended override locations:

- **Per developer machine:** `~/.gradle/gradle.properties`
- **Per CI job:** environment variables like `MAP_STYLE_URL`, `MAP_STYLE_FALLBACK_URL`, and `ALLOW_HTTP_MAP_STYLE_URL`
- **Per invocation:** `./gradlew assembleDebug -PMAP_STYLE_URL=https://maps.example.com/styles/mobile/style.json`

Example development overrides:

```properties
MAP_STYLE_URL=http://10.0.2.2:8080/styles/dev/style.json
ALLOW_HTTP_MAP_STYLE_URL=false
```

Example production/hosted overrides:

```properties
MAP_STYLE_URL=https://maps.example.com/styles/mobile/style.json
MAP_STYLE_FALLBACK_URL=https://maps.example.com/styles/mobile/fallback.json
ALLOW_HTTP_MAP_STYLE_URL=false
```

For a fuller deployment guide, including self-hosted stack notes, see [`docs/map_style_configuration.md`](docs/map_style_configuration.md).

## Explore foundation

The app now includes an initial **Explore** experience built for clean architecture first, not fake completeness:

- A top-level **Explore** entry with the prompt **“Take me Somewhere…”**.
- Intent chips for: Delicious, Fun, Open Now, I've Never Been, Quiet, Outdoors, Important, Close to Home, On My Way, Special, New, I Can Shop, I Can Learn, Good for Kids, and Having a Sale.
- A dedicated Explore results screen showing ranked cards with address, status/timing, distance or off-route distance, grouped review summaries, source attribution, confidence-labeled event/promotion signals, a subtle internal-review badge when available, and a short explanation of why each suggestion was chosen.
- A dedicated in-app **Place Detail** destination screen for Explore results with loading, partial-data, empty, and error states plus action buttons for navigation, map preview, save, reviews, and internal review authoring.
- Separate **See Reviews** and **Leave Review** flows. Internal Simon Says GPS reviews are always shown first and are never blended into provider review counts or summaries.
- A dedicated Explore settings screen persisted through DataStore.

### Explore architecture

The Explore foundation is split across the existing module boundaries:

- `:navigation` now owns the Explore domain models, provider contracts, heuristics, ranking engine, orchestrator abstraction, and ranking/unit tests.
- `:app` owns DataStore persistence for `ExploreSettings`, local internal reviews, provider implementations and aggregation wiring, lightweight Explore caching, duplicate merging, place-detail/review repositories, and the Compose Explore screens.

Core Explore contracts introduced in this PR:

- `ExploreCategory`
- `ExploreSettings`
- `ExploreQuery`
- `ExploreCandidate`
- `ExploreResult`
- `ExploreReason`
- `ExploreRepository`
- `ExploreOrchestrator`
- Provider interfaces for place discovery, place details, reviews, events, promotions, and visit history enrichment

### Explore provider strategy in this phase

This PR intentionally keeps the architecture provider-ready without pretending every integration is equally mature:

- **Fully implemented now:** a real Nominatim-backed place discovery + place-details enrichment path using public OpenStreetMap/Nominatim data.
- **Implemented locally now:** device-scoped internal review persistence with aggregate surfacing on Explore cards, Place Detail, and the dedicated review list flow.
- **Scaffolded cleanly behind capability contracts:** curated provider review summaries, event and promotion providers, plus internal recent-destination visit history enrichment.
- **Aggregation now merges duplicates** across provider outputs using provider links, normalized names/addresses, phone numbers, and coordinate proximity.
- **Source attribution and confidence metadata** flow through the repository, ranking engine, and Explore cards.
- **Provider availability and partial failures** are surfaced in the UI so fallbacks remain visible and debuggable.

See [`docs/explore_provider_integration.md`](docs/explore_provider_integration.md) for the provider matrix, setup expectations, attribution rules, and limitations.
See [`docs/place_detail_reviews.md`](docs/place_detail_reviews.md) for the dedicated Place Detail screen, internal review persistence, and internal-vs-external review display rules.

### Personalized Explore and routing context

This PR adds a local-first personalization layer on top of the Explore foundation:

- **First-party visit history:** Simon Says GPS stores app-confirmed saves and navigation arrivals locally when visit history is enabled. There is no assumed Google Maps Timeline import or other external history dependency in this build.
- **Privacy controls:** Explore Settings now includes enable/disable, retention windows, per-place removal, and clear-all history actions.
- **I've Never Been:** Results confidently matched in first-party history are filtered out. Lower-confidence matches are treated cautiously instead of being overclaimed.
- **Close to Home:** Explore can score suggestions against a saved home anchor and configurable home radius.
- **On My Way:** When navigation is active, Explore now uses active route geometry, distance-off-route, and estimated detour minutes to keep corridor logic explainable and bounded.
- **Transport profiles:** Settings now persist profiles for walking, bicycle, e-bike, e-skateboard, motorcycle, car, RV, truck/commercial, and trailer/towing.
- **Route styles and gameplay:** Settings now persist Fastest, Scenic, No tolls, Low stress, and Simon Challenge Mode preferences plus challenge intensity.

### Honest routing limitations

Profile-aware routing is scaffolded now, but the current provider path still has important limits:

- truck/RV/trailer/height/length/weight restrictions are **not guaranteed** to be enforced by the current provider stack in this repo
- toll avoidance, scenic routing, and low-stress routing are saved and surfaced as preferences, but are not universally guaranteed by the current providers
- Simon Challenge Mode is intentionally bounded and does **not** generate absurd loops or pretend to be a full alternate route generator yet

These limitations are shown in Settings, route preview messaging, and docs so the app does not overclaim safety-critical support.

### Explore settings now implemented

Persisted settings now include:

- default radius in miles
- require open now by default
- suggestion count (`1 auto-pick` or `3 choices`)
- allow route detours while navigating
- max detour distance/time
- use event data when available
- use internal reviews first
- include third-party review summaries when available
- home reference plus Close to Home radius (saved from current location in this phase)
- visit history enable/disable and retention window
- surprise-me weighting
- kid-friendly filter
- quiet-preference strictness
- accessible-places preference
- avoid alcohol-focused venues
- avoid adult-oriented venues

### Explore limitations and follow-up work

Implemented now:

- explainable ranking pipeline with open-now, event timing, rating confidence, route hooks, visit history, and confidence-labeled sale/newness inputs
- real Nominatim-backed place discovery and detail enrichment
- duplicate place merging with provider link preservation and source attribution
- grouped review-source ordering with internal reviews first, explicit external-provider summary blocks, and no fake combined global rating count
- partial provider failure handling plus lightweight Explore snapshot caching
- Compose Explore shell, enriched results cards, dedicated Place Detail / reviews / leave-review screens, and settings UI
- first-run walkthrough stub
- ranking/category/settings/repository tests

Still future work:

- additional live review/event/deal providers beyond the current Nominatim place path
- optional backend sync for internal reviews, richer authenticated identity, moderation/report workflows, reactions, photos, and voice-first review drafting handoff
- richer home-address search/selection flow
- deeper route-detour estimation tied to routing-provider ETAs
- true provider-enforced heavy-vehicle restrictions and richer style-specific route generation
- analytics/telemetry and moderation around review content

**No binary files were added in this PR.**


## Voice assistant input layer

This PR adds a **voice-first input architecture** that complements the existing TTS/navigation stack instead of replacing it.

### Implemented now

- A dedicated voice input domain stack with `VoiceAssistantManager`, `SpeechCaptureManager`, `VoiceIntentParser`, `VoiceActionDispatcher`, `CrowdReportRepository`, `ReviewDraftRepository`, `ProseCleanupService`, and `MusicIntentProvider` abstractions.
- A new **Voice Assistant** UI entry point from the map screen with large controls for typed/manual command entry, passenger-friendly reporting, review dictation drafting, and soundtrack intent scaffolding.
- Transcript handling that keeps the **raw transcript**, **interpreted intent**, and **resulting action** separate.
- Voice/reporting support for commands in the spirit of:
  - “Simon, find coffee on my way”
  - “Simon, take me somewhere fun nearby”
  - “Simon, report speed trap / police / traffic / accident / pothole / roadwork / disabled vehicle / something awesome”
  - “Simon, leave a review for this place”
  - “Simon, make me a spooky road trip playlist”
- Structured crowd reports with timestamp, location, report type, transcript note, confidence, explicit confirmation state, and moderation status fields.
- Voice review drafts that preserve raw dictated text, optional cleanup suggestions, and final approved text separately.
- Settings toggles for microphone/voice assistant enablement, spoken confirmations, hands-free reporting, AI cleanup opt-in, and soundtrack integration scaffolding.

### Scaffolded only in this phase

- real always-listening wake words
- provider-backed speech recognition
- external review publishing
- provider-specific AI prose cleanup
- live Spotify/Apple Music/YouTube Music/Pandora SDK integrations
- automatic emergency escalation

### Permissions, privacy, and limitations

- `RECORD_AUDIO` is now requested for user-initiated voice capture.
- Voice capture is **not** always listening in this PR.
- Crowd reports and review drafts are staged locally in-memory in this implementation.
- Crowd reports require an explicit confirmation step before submission.
- Soundtrack requests are routed through a provider-agnostic abstraction and currently return a demo/stub response.
- See [`docs/voice_assistant.md`](docs/voice_assistant.md) for the detailed implementation/scaffolding breakdown.

**No binary files were added in this PR.**

## Simon Says rules

### Basic mode

Every maneuver is marked `REQUIRED_SIMON_SAYS`. The navigation engine now uses step proximity, projected route-corridor distance, heading-confidence checks, and short intersection grace windows before deciding that a required turn was actually taken or actually missed.

### Mischief mode

Alternating maneuvers are flagged `NORMAL_INFO_ONLY`, creating fake-out turns. Unauthorized-turn detection is still intentionally explainable rather than magic: it looks for a maneuver-shaped heading change near the active step while the driver remains inside the route corridor, then applies bounded cooldowns so the app does not repeatedly punish the same noisy intersection sample.

## UI instrumentation and screenshot-oriented tests

- `KeyFlowsScreenshotTest` covers destination search, route preview, active navigation, settings, and debug-overlay visibility with deterministic `AppUiState` fixtures.
- The instrumentation suite injects a fake map panel instead of loading live tiles, so the tests stay demo-mode-friendly and avoid network-driven flakes.
- Compose screenshots are captured in-memory for each scenario; optional PNG export is available only at test runtime and writes outside the repo checkout.
- Android test animations are disabled in Gradle to keep CI runs as stable as practical.

### Run locally

Compile the debug app plus instrumentation suite:

```bash
gradle :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest
```

Run the UI/instrumentation suite on a connected emulator or device:

```bash
gradle :app:connectedDebugAndroidTest
```

Optionally export screenshot PNGs while the tests run:

```bash
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.recordScreenshots=true
```

When screenshot export is enabled, files are written to `/sdcard/Android/data/com.simonsaysgps/files/ui-test-snapshots/` on the device/emulator so they can be reviewed locally without committing generated artifacts. See `docs/ui_instrumentation_testing.md` for the full workflow.

## Testing the navigation logic

- Enable **Demo mode** to use a fake location stream in the emulator.
- Switch **Prompt personality** in Settings to preview different tones without changing navigation rules.
- Switch **Routing provider** in Settings to validate provider resolution and fallback behavior.
- Enable **Debug overlay** to inspect GPS coordinates, step index, next maneuver distance, active-step distance, route corridor status, corridor distance/threshold, heading confidence, hysteresis state, reroute suppression reasons, transition reasons, authorization state, arrival state, and last reroute reason. Search and route status messages also call out when cached data is being used because a request timed out or the device is offline.
- Unit tests cover prompt generation, authorization assignment, unauthorized-turn detection, near-intersection jitter suppression, slight heading drift, slow valid-turn approaches, missed-turn logic, reroute cooldown behavior, arrival-state transitions, step-progression stability, navigation banner mapping, provider selection/fallback behavior, routing repository mapping behavior, search debouncing, recent destination persistence/mapping behavior, and navigation-session restoration/storage behavior.

## Enhanced active navigation UX

- Active guidance now promotes a richer maneuver banner with step progress, turn-type context, road labeling, and a clearer explanation of whether the current instruction is a real **Simon Says** move or informational-only.
- When the final step resolves, the navigation state explicitly transitions through `APPROACHING_DESTINATION` to `ARRIVED`, allowing the UI to show a dedicated arrival banner instead of falling back to a generic empty-next-step message.
- Lane-guidance-ready abstractions now exist in the domain and UI layers. The current OSRM/GraphHopper providers do not yet populate lane-level data, so the UI shows a Compose-native placeholder that explains lane guidance will appear when provider support lands.
- Navigation heuristics now use projected polyline snapping, dynamic route-corridor thresholds, near-intersection grace periods, heading-confidence-aware turn detection, bounded reroute cooldowns, and a short post-step progression lock to reduce false reroutes and adjacent-step oscillation.
- Arrival handling is intentionally safer near the destination edge: once the final maneuver is effectively satisfied at low speed, arrival latches instead of immediately bouncing back into reroute logic because of one noisy sample.
- No binary files were added for this UX update.

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
- The checked-in fallback map style remains a development-safe default and should be replaced by environment configuration in real production deployments.
- GraphHopper support depends on a configured API key and currently uses a lightweight maneuver mapping based on the Directions API instruction sign values.
- Valhalla is scaffolded for selection/configuration but not yet implemented as a concrete routing adapter.
- Offline support in this phase is intentionally lightweight: repeated destination queries and the latest matching route preview are cached, but the app does not perform full offline routing.
- Turn detection heuristics now combine projected route proximity, heading deltas, heading-confidence checks, intersection hysteresis, step-distance trends, and reroute cooldowns; they are still intentionally understandable heuristics rather than full map matching.
- The app still does not claim lane-level localization, sensor fusion, or production-grade map matching, so dense urban canyons, bad heading data, or incomplete provider maneuvers can still produce edge cases.
- Navigation/domain logic now lives in `:navigation`, while external routing/geocoding adapters live in `:providers`, leaving `:app` focused on Android wiring and UI.
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

- Keep trimming Android-specific concerns out of `:navigation` as the app grows, while avoiding unnecessary module sprawl.
- Continue refining route snapping and off-route heuristics, especially for dense intersections, stacked roads, and provider geometries with sparse maneuver placement.
- Expand lane guidance from the current provider-ready UI/domain scaffold into provider-backed lane-level instructions.
- Expand the partial Valhalla scaffold into a concrete adapter.
- Finish wiring a richer settings/debug surface for inspecting resolved map-style metadata at runtime if operational support needs it later.
- Swap demo Explore providers with production-backed place/event/review/deal integrations behind the same contracts.
- Extend the Explore review/save quick-action stubs into fully backed account-aware flows.

## TODOs

- Keep tuning missed-turn, unauthorized-turn, and arrival heuristics against more real-world traces; the current logic is deliberately bounded and explainable, not a full map-matching stack.
- Expand lightweight cache coverage and retry/backoff policies as the app grows.
