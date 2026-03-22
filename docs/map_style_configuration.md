# Map style deployment and configuration

Simon Says GPS intentionally does **not** ship proprietary map styles, private tokens, offline packs, or self-hosted tile assets in this repository. Instead, the app exposes environment-driven configuration hooks so a real deployment can point MapLibre at an appropriate hosted style stack.

## Configuration model

The Android app resolves map style configuration from these inputs:

- `MAP_STYLE_URL`: primary MapLibre style JSON URL.
- `MAP_STYLE_FALLBACK_URL`: fallback style URL used when the primary value is blank or invalid.
- `ALLOW_HTTP_MAP_STYLE_URL`: security escape hatch for HTTP-only development environments. Defaults to `false`.

The Gradle build reads each value from:

1. a Gradle property, then
2. an environment variable with the same name, then
3. the checked-in default.

That makes these workflows possible without modifying source:

- local developer overrides in `~/.gradle/gradle.properties`
- CI/CD injection through environment variables
- temporary command-line overrides such as `-PMAP_STYLE_URL=...`

## Safe defaults in the repo

The repo keeps the app runnable by default:

- `MAP_STYLE_URL` is intentionally blank in `gradle.properties`
- `MAP_STYLE_FALLBACK_URL` points to `https://demotiles.maplibre.org/style.json`
- `ALLOW_HTTP_MAP_STYLE_URL=false`

If no environment-specific production style is configured, the app still starts and renders a public development-safe map.

## Validation behavior

At runtime the app validates the configured map style URL before loading it:

- blank primary URL -> fallback is used
- malformed/non-absolute URL -> fallback is used
- non-HTTP(S) URL -> fallback is used
- remote HTTP URL while `ALLOW_HTTP_MAP_STYLE_URL=false` -> fallback is used

When fallback happens, the app:

- logs a warning
- renders the fallback style so the map remains functional
- shows a lightweight in-app notice over the map to make misconfiguration visible during testing

`localhost`, `127.0.0.1`, and `10.0.2.2` are allowed over HTTP even when the insecure flag remains `false`, which keeps emulator-based self-hosted development simple.

## Recommended production setup

For a real deployment, host a complete MapLibre-compatible style stack outside the repo. Typical pieces include:

- a style JSON endpoint
- vector or raster tile endpoints referenced by that style
- glyph endpoints
- sprite endpoints

Example production configuration:

```bash
export MAP_STYLE_URL="https://maps.example.com/styles/mobile/style.json"
export MAP_STYLE_FALLBACK_URL="https://maps.example.com/styles/mobile/fallback.json"
export ALLOW_HTTP_MAP_STYLE_URL="false"
```

If you use private infrastructure, inject credentials through your deployment environment or authenticated edge infrastructure. Do **not** commit tokens, private URLs containing embedded secrets, or proprietary style JSON blobs into this repository.

## Self-hosted development stack examples

Possible setups include:

- TileServer GL / MapTiler Server / Tegola-style stacks fronted by a style JSON URL
- custom CDN-hosted style JSON with sprite/glyph/tile references
- a Docker-based local tile stack exposed to the Android emulator through `http://10.0.2.2:<port>/...`

Example emulator-friendly local override:

```properties
MAP_STYLE_URL=http://10.0.2.2:8080/styles/dev/style.json
ALLOW_HTTP_MAP_STYLE_URL=false
```

This works because `10.0.2.2` is treated as a local development host. If you truly need a non-local HTTP endpoint for a temporary lab environment, set:

```properties
ALLOW_HTTP_MAP_STYLE_URL=true
```

Keep that override out of production.

## Operational recommendations

- Prefer HTTPS for every remotely hosted map asset.
- Keep style JSON, sprite, glyph, and tile endpoints versioned.
- Separate public tile delivery concerns from secrets; use signed URLs, auth gateways, or network-layer controls instead of embedding secrets in the app config.
- Validate that your style JSON references reachable glyph/sprite/tile endpoints before promoting a build.
- Consider using distinct style URLs per environment, such as `dev`, `staging`, and `prod`.

## What this repo intentionally does not include

This PR intentionally does **not** add:

- binary map assets
- offline tile packs
- proprietary style files
- API secrets
- private keys

That keeps the repository safe to share while still providing production-ready configuration scaffolding.
