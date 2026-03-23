# Voice assistant input layer

## What is implemented now

This PR adds a voice-first input layer that sits beside the existing navigation/TTS output layer instead of overloading it.

Implemented pieces:

- `VoiceAssistantManager` orchestration for transcript handling and intent dispatch.
- `SpeechCaptureManager` abstraction with a stub/manual transcript capture implementation for safe UI-driven testing.
- `VoiceIntentParser` rule-based parsing for route-aware search prompts, Explore prompts, crowd reports, review drafting commands, review cleanup requests, and soundtrack requests.
- `VoiceActionDispatcher` separation so parsing, intent interpretation, and side effects remain distinct.
- In-memory `CrowdReportRepository` and `ReviewDraftRepository` implementations for staged report/review flows.
- Structured crowd report models with timestamp, location, transcript note, confidence, user confirmation state, and moderation status.
- Review draft models that preserve raw transcript, optional cleanup suggestion, and final approved text separately.
- `ProseCleanupService` abstraction with a stub cleanup implementation.
- `MusicIntentProvider` abstraction with a demo soundtrack provider that stores the requested vibe without assuming a real SDK.
- Compose UI for a voice assistant entry point, manual reporting buttons, review dictation drafting, and permissions/status messaging.
- Settings hooks for microphone/voice assistant enablement, spoken confirmations, hands-free reporting, AI cleanup opt-in, and soundtrack scaffolding toggles.

## What is scaffolded only

The following areas are intentionally scaffolded and not represented as production-complete integrations in this PR:

- real always-listening wake word detection
- live cloud/on-device speech-to-text provider integration
- external review write-back to third-party platforms
- AI provider-specific cleanup calls
- proprietary music service SDK connections and live playlist creation
- automatic emergency escalation for accident reports

## Permissions and privacy

- The app now requests `RECORD_AUDIO` for voice input.
- Voice input is user-initiated from the UI in this PR; there is no hidden always-listening mode.
- Crowd reports and review drafts in this PR are stored in app memory for the current process, not uploaded to an external service by default.
- Review cleanup is opt-in and abstracted so future providers can be added without changing the surrounding UX contract.
- Spoken confirmations can be turned off in Settings if the user prefers less audio chatter.

## Safety limitations

- Crowd reports are staged first and require explicit confirmation before submission.
- Manual passenger-friendly buttons are available so riders do not need to dictate every report.
- The current speech capture implementation is a safe scaffold and should not be interpreted as a production-grade wake-word or hands-free driving guarantee.
- Accident reporting does not automatically contact emergency services.

## Binary file confirmation

No binary files were added for this PR.
