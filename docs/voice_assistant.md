# Voice assistant input layer

## What is fully working in v1

This repo now ships a local-first, user-initiated voice assistant flow that complements the existing navigation/TTS stack instead of replacing it.

Implemented pieces:

- `VoiceAssistantManager` orchestration for transcript handling and intent dispatch.
- `SpeechCaptureManager` backed by Android's native `SpeechRecognizer` when available, while still allowing typed transcript entry as a first-class fallback.
- Rule-based `VoiceIntentParser` coverage for Explore requests, On My Way requests, crowd reports, review drafting commands, review cleanup requests, and soundtrack requests.
- `VoiceActionDispatcher` separation so capture, parsing, intent interpretation, and side effects stay decoupled.
- Local DataStore-backed `CrowdReportRepository` persistence for staged and submitted reports so they survive process death.
- Local DataStore-backed `ReviewDraftRepository` persistence so active/approved review drafts survive process death.
- Structured crowd report models with timestamp, location, transcript note, confidence, explicit user confirmation state, and moderation status.
- Review draft models that preserve raw transcript, optional cleanup suggestion, and final approved text separately.
- `ProseCleanupService` abstraction with a local stub cleanup implementation.
- `MusicIntentProvider` abstraction with a demo soundtrack provider that stores the requested vibe without pretending a live provider SDK is wired up.
- Compose UI for a clearer voice assistant entry point, microphone permission messaging, typed/manual command fallback, pending/submitted report visibility, and saved review draft visibility.
- Settings hooks for microphone/voice assistant enablement, spoken confirmations, hands-free reporting toggle state, AI cleanup opt-in, and soundtrack scaffolding toggles.

## What remains scaffolded only

The following areas are intentionally scaffolded and are not represented as production-complete integrations in this PR:

- always-listening wake word detection
- hidden/background microphone capture
- external review write-back to third-party platforms
- provider-specific AI cleanup calls
- proprietary music service SDK connections and live playlist creation
- automatic emergency escalation for accident reports

## Permissions and privacy

- The app requests `RECORD_AUDIO` for voice capture.
- Voice capture is explicitly user-initiated from the Voice Assistant screen in this build.
- There is no hidden always-listening mode.
- Crowd reports and review drafts are stored locally on-device via DataStore in this PR and are not uploaded to an external backend by default.
- Review cleanup remains opt-in.
- Spoken confirmations can be turned off in Settings.

## Safety and product limitations

- Crowd reports are staged first and still require explicit confirmation before submission.
- Passenger-friendly tap targets remain available so riders do not need to dictate every report.
- Native speech recognition depends on Android speech services being available on the device; typed transcript entry remains the fallback path.
- Soundtrack/music requests are intentionally scaffolded and currently store intent plus messaging only.
- Accident reporting does not contact emergency services.

## Example supported commands

- “Simon, find coffee on my way”
- “Simon, what is on my way”
- “Simon, take me somewhere quiet”
- “Simon, report police ahead”
- “Simon, report traffic”
- “Simon, report pothole”
- “Simon, leave a review for this place”
- “Simon, make me a beach playlist”

## Binary file confirmation

No binary files were added for this PR.
