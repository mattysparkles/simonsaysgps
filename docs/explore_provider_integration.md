# Explore provider integration

This document describes the current Explore aggregation stack introduced after the initial Explore foundation.

## Fully implemented provider path

The fully working provider-backed path in this repo is:

1. `NominatimPlaceDiscoveryProvider`
2. `NominatimPlaceDetailsProvider`
3. `DefaultExploreRepository` duplicate merging + enrichment
4. `ExploreRankingEngine` ranking and explainability
5. Compose Explore result cards with source attribution, grouped reviews, event/promo timing, and confidence hints

### What is real today

- Place discovery is backed by the public Nominatim search API.
- Place details enrichment reuses Nominatim `extratags` and address metadata already fetched during discovery.
- Provider attribution is carried into the normalized Explore models and shown in the UI.
- Duplicate merging preserves provider links for debugging and future cross-provider detail lookups.

## Scaffolded providers in this PR

The following providers are intentionally scaffolded behind the same capability contracts so they can be swapped out later without reworking the UI or ViewModels:

- `CuratedReviewProvider`
- `CuratedEventProvider`
- `CuratedPromotionSignalProvider`
- `RecentDestinationVisitHistoryProvider`

### Why these are scaffolded

The app now has the aggregation, ordering, attribution, and partial-failure behavior needed for production providers, but this repo does not yet include licensed commercial review/event/deal APIs or authenticated internal review storage.

## Aggregation and merging behavior

Places are merged into a canonical Explore result when signals strongly suggest the same venue:

- explicit provider ID link match
- normalized name match + close coordinates
- normalized name + normalized address overlap + close coordinates
- matching phone numbers when available

Each canonical result preserves:

- all provider links
- all source attributions
- confidence metadata for inferred signals
- grouped review summaries by source

## Review behavior

- Internal review summaries are ordered before third-party summaries.
- Third-party reviews stay grouped by provider.
- Counts are not collapsed into a fake universal rating source label.
- Summary-only providers stay labeled as summary-only.

## Event and promotion behavior

- Events support happening now, starting soon, and upcoming/all-day style ranking signals.
- Promotion signals support both provider-backed and inferred sale/special hints.
- Inferred promotions are labeled with lower confidence and are never treated as verified facts.

## Setup and configuration

No new secret or vendor credential is required for this PR.

Relevant existing configuration:

- `NOMINATIM_BASE_URL` for place discovery and detail enrichment.
- Existing Explore settings for radius, open-now preference, event usage, route detours, and review ordering.

## Attribution requirements

When surfacing provider-backed Explore results:

- keep provider labels visible in the card/detail flow
- preserve grouped review source labels
- do not present inferred promotion/newness signals as verified
- keep OpenStreetMap / Nominatim attribution intact where those results are used

## Known limitations

- Nominatim is the only fully implemented end-to-end external Explore provider path in this PR.
- Event, review, and promotion providers are scaffolded rather than commercial/live integrations.
- Review quick actions are still UI stubs; this PR focuses on aggregation and enrichment.
- Android SDK availability is still required to run full Gradle Android tests locally.

## Binary file confirmation

No binary files were added by this Explore provider integration work.
