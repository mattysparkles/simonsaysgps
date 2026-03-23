# Place Detail and internal reviews

This PR turns Explore results into real in-app destinations instead of leaving review actions as stubs.

## What ships now

- A dedicated **Place Detail** screen for Explore results.
- A dedicated **See Reviews** screen that keeps internal reviews first.
- A dedicated **Leave Review** compose flow with rating, freeform text, and lightweight tags:
  - Quiet
  - Good for kids
  - Scenic
  - Fun
  - Delicious
  - Accessible
  - Crowded
  - Expensive
- Local persistence for internal reviews using the app's existing DataStore + repository pattern.
- Provider-backed review summaries shown separately beneath internal reviews.

## Place identity strategy

Reviews are keyed by a canonical place id generated from normalized place name, normalized address, and rounded coordinates. This keeps local review storage more stable than raw provider ids alone and avoids making persistence depend on any single external place provider.

The same canonical id is used for:

- Explore result review badges
- Place Detail state
- local internal review storage
- future backend-sync hooks

## Internal review persistence approach

Internal reviews are stored locally in DataStore as JSON-encoded review records. Each record includes:

- `internalReviewId`
- `canonicalPlaceId`
- `authorDisplayName`
- `rating`
- `reviewText`
- `createdAtEpochMillis`
- `updatedAtEpochMillis`
- `tags`
- `visitContext`
- `moderationStatus`
- `source = internal`

This implementation is intentionally device-scoped for now. It does **not** require a full account system, remote moderation service, or provider write-back pipeline.

## Internal vs external review display rules

- Internal Simon Says GPS reviews are always shown first.
- Internal counts and averages are computed only from locally stored internal reviews.
- External/provider review blocks remain source-attributed and separate.
- The app does **not** merge internal review counts into a fake universal total.
- If a provider only allows summary display, the UI shows a provider summary block rather than pretending full third-party review detail exists.

## Future-ready hooks added in this PR

The architecture now leaves room for:

- provider-backed rich place metadata expansion
- optional backend sync for internal reviews
- moderation/reporting workflows
- reactions and richer community signals
- photo reviews
- voice-drafted review handoff into the compose flow

## Current limitations

- Internal reviews are local to the current device/app storage.
- External reviews remain summary-only in this repo unless a provider safely exposes richer detail.
- The report-review action is a placeholder hook, not a full moderation implementation yet.
- Review drafts are preserved in the active app state for the compose flow, not synced across devices.

## Binary-file confirmation

No binary files were added for this feature. No `.jar`, `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`, `.mp4`, `.mov`, `.so`, `.aar`, `.apk`, or `.aab` artifacts were introduced by this change.
