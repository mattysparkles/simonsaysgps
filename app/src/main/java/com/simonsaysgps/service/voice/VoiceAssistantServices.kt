package com.simonsaysgps.service.voice

import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.CrowdReportType
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import com.simonsaysgps.domain.model.voice.SoundtrackIntent
import com.simonsaysgps.domain.model.voice.SoundtrackResult
import com.simonsaysgps.domain.model.voice.SpeechCaptureState
import com.simonsaysgps.domain.model.voice.VoiceContext
import com.simonsaysgps.domain.model.voice.VoiceIntent
import com.simonsaysgps.domain.repository.voice.CrowdReportRepository
import com.simonsaysgps.domain.repository.voice.ReviewDraftRepository
import com.simonsaysgps.domain.service.voice.MusicIntentProvider
import com.simonsaysgps.domain.service.voice.ProseCleanupService
import com.simonsaysgps.domain.service.voice.SpeechCaptureManager
import com.simonsaysgps.domain.service.voice.VoiceActionDispatcher
import com.simonsaysgps.domain.service.voice.VoiceAssistantManager
import com.simonsaysgps.domain.service.voice.VoiceDispatchResult
import com.simonsaysgps.domain.service.voice.VoiceIntentParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Singleton
class StubSpeechCaptureManager @Inject constructor() : SpeechCaptureManager {
    private val _captureState = MutableStateFlow<SpeechCaptureState>(SpeechCaptureState.Idle)
    override val captureState: StateFlow<SpeechCaptureState> = _captureState.asStateFlow()

    override fun startListening() {
        _captureState.value = SpeechCaptureState.Listening
    }

    override fun stopListening() {
        _captureState.value = SpeechCaptureState.Idle
    }

    override fun submitTranscript(transcript: String) {
        _captureState.value = if (transcript.isBlank()) {
            SpeechCaptureState.Error("Simon didn't catch that. Try a short, direct command.")
        } else {
            SpeechCaptureState.TranscriptAvailable(transcript.trim())
        }
    }
}

@Singleton
class RuleBasedVoiceIntentParser @Inject constructor() : VoiceIntentParser {
    override fun parse(transcript: String): VoiceIntent {
        val normalized = transcript.lowercase().removePrefix("simon,").removePrefix("simon").trim()
        if (normalized.isBlank()) return VoiceIntent.Unknown
        if (normalized in setOf("yes", "confirm", "submit it", "send it")) return VoiceIntent.Confirmation(true)
        if (normalized in setOf("no", "cancel", "never mind", "dismiss")) return VoiceIntent.Confirmation(false)
        if (normalized.contains("leave a review")) return VoiceIntent.ReviewCurrentPlace
        if (normalized.contains("clean up grammar")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.CLEAN_UP_GRAMMAR)
        if (normalized.contains("make shorter")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.MAKE_SHORTER)
        if (normalized.contains("make funnier")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.MAKE_FUNNIER)
        if (normalized.contains("more factual")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.MAKE_MORE_FACTUAL)
        if (normalized.contains("keep as is") || normalized.contains("keep as-is")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.KEEP_AS_IS)
        if (normalized.contains("playlist")) {
            val vibe = normalized.substringBefore("playlist")
                .removePrefix("make me")
                .trim()
                .removePrefix("a ")
                .removePrefix("an ")
                .ifBlank { "road trip" }
            return VoiceIntent.Soundtrack(vibe)
        }
        val reportType = when {
            normalized.contains("speed trap") || normalized.contains("report police") -> CrowdReportType.SPEED_TRAP
            normalized.contains("report traffic") -> CrowdReportType.TRAFFIC
            normalized.contains("report accident") -> CrowdReportType.ACCIDENT
            normalized.contains("disabled vehicle") -> CrowdReportType.DISABLED_VEHICLE
            normalized.contains("pothole") || normalized.contains("road hazard") -> CrowdReportType.POTHOLE
            normalized.contains("roadwork") || normalized.contains("closure") -> CrowdReportType.ROADWORK
            normalized.contains("checkpoint") -> CrowdReportType.CHECKPOINT
            normalized.contains("awesome") || normalized.contains("local tip") -> CrowdReportType.LOCAL_TIP
            normalized.contains("scenic") || normalized.contains("cool attraction") -> CrowdReportType.SCENIC_ATTRACTION
            else -> null
        }
        if (normalized.startsWith("report") && reportType != null) {
            val note = normalized.removePrefix("report").trim().takeIf { it != reportType.label.lowercase() }
            val confidence = when {
                normalized.contains("speed trap") || normalized.contains("traffic") -> 0.9f
                else -> 0.8f
            }
            return VoiceIntent.CrowdReport(reportType, note = note, confidence = confidence)
        }
        if (normalized.contains("coffee") && normalized.contains("on my way")) {
            return VoiceIntent.SearchOnRoute("coffee")
        }
        if (normalized.contains("fun nearby")) return VoiceIntent.Explore(ExploreCategory.FUN)
        if (normalized.contains("good to eat") || normalized.contains("somewhere to eat")) return VoiceIntent.Explore(ExploreCategory.DELICIOUS)
        if (normalized.contains("never been")) return VoiceIntent.Explore(ExploreCategory.NEVER_BEEN)
        if (normalized.contains("on my way")) {
            val query = normalized.substringAfter("find", "").substringBefore("on my way").trim().ifBlank { "nearby stop" }
            return VoiceIntent.SearchOnRoute(query)
        }
        return VoiceIntent.Unknown
    }
}

@Singleton
class StubProseCleanupService @Inject constructor() : ProseCleanupService {
    override suspend fun cleanup(rawText: String, option: ReviewCleanupOption): String? {
        if (rawText.isBlank()) return null
        return when (option) {
            ReviewCleanupOption.KEEP_AS_IS -> rawText
            ReviewCleanupOption.CLEAN_UP_GRAMMAR -> rawText.replaceFirstChar { it.uppercase() }.trimEnd('.') + "."
            ReviewCleanupOption.MAKE_SHORTER -> rawText.split(' ').take(12).joinToString(" ")
            ReviewCleanupOption.MAKE_FUNNIER -> "$rawText Bonus points for making the drive feel like a movie montage."
            ReviewCleanupOption.MAKE_MORE_FACTUAL -> rawText.split('.').first().trim().let { "$it. Clean, direct, and easy to verify." }
        }
    }
}

@Singleton
class DemoMusicIntentProvider @Inject constructor() : MusicIntentProvider {
    override suspend fun handle(intent: SoundtrackIntent): SoundtrackResult = SoundtrackResult(
        providerName = intent.providerHint ?: "Demo soundtrack provider",
        message = "Soundtrack scaffolding saved the vibe '${intent.vibe}' for a future music integration.",
        deepLink = null,
        launched = false
    )
}

@Singleton
class DefaultVoiceActionDispatcher @Inject constructor(
    private val crowdReportRepository: CrowdReportRepository,
    private val reviewDraftRepository: ReviewDraftRepository,
    private val proseCleanupService: ProseCleanupService,
    private val musicIntentProvider: MusicIntentProvider
) : VoiceActionDispatcher {
    override suspend fun dispatch(intent: VoiceIntent, context: VoiceContext): VoiceDispatchResult {
        return when (intent) {
            is VoiceIntent.SearchOnRoute -> VoiceDispatchResult.Search(
                query = intent.query,
                onMyWay = true,
                spokenConfirmation = "Looking for ${intent.query} along your route."
            )

            is VoiceIntent.Explore -> VoiceDispatchResult.Explore(
                spokenConfirmation = "Opening ${intent.category.displayName.lowercase()} suggestions nearby."
            )

            is VoiceIntent.CrowdReport -> {
                val report = CrowdReport(
                    id = "report-${System.currentTimeMillis()}",
                    timestampEpochMillis = System.currentTimeMillis(),
                    location = context.currentLocation,
                    type = intent.type,
                    transcriptNote = intent.note,
                    confidence = ((intent.confidence * 100).roundToInt() / 100f),
                    userConfirmed = false,
                    status = com.simonsaysgps.domain.model.voice.CrowdReportStatus.DRAFT
                )
                crowdReportRepository.stage(report)
                VoiceDispatchResult.ReportStaged("I heard ${intent.type.label.lowercase()}. Say yes to submit the report.")
            }

            VoiceIntent.ReviewCurrentPlace -> {
                reviewDraftRepository.startDraft(
                    ReviewDraft(
                        id = "review-${System.currentTimeMillis()}",
                        place = context.selectedPlace
                    )
                )
                VoiceDispatchResult.ReviewStarted(
                    spokenConfirmation = if (context.selectedPlace != null) {
                        "Ready to draft a review for ${context.selectedPlace.name}. Dictate your note when you're ready."
                    } else {
                        "Ready to draft a quick review. Pick a place first for the final save target."
                    }
                )
            }

            is VoiceIntent.ReviewCleanup -> {
                val current = reviewDraftRepository.activeDraft.first()
                val cleaned = proseCleanupService.cleanup(current?.rawTranscript.orEmpty(), intent.option)
                reviewDraftRepository.applyCleanupSuggestion(intent.option, cleaned)
                VoiceDispatchResult.ReviewUpdated("Prepared a ${intent.option.label.lowercase()} suggestion for your review.")
            }

            is VoiceIntent.Soundtrack -> {
                val result = musicIntentProvider.handle(SoundtrackIntent(vibe = intent.vibe))
                VoiceDispatchResult.SoundtrackQueued(result)
            }

            is VoiceIntent.Confirmation -> {
                if (intent.accepted) {
                    crowdReportRepository.confirmPending()
                    VoiceDispatchResult.ReportSubmitted("Thanks. Your report is queued for moderation.")
                } else {
                    crowdReportRepository.dismissPending("User cancelled voice confirmation")
                    VoiceDispatchResult.NoOp("Okay, I dropped that pending voice action.")
                }
            }

            VoiceIntent.Unknown -> VoiceDispatchResult.NoOp("I can help with places, reports, reviews, and playlists. Try a shorter command.")
        }
    }
}

@Singleton
class DefaultVoiceAssistantManager @Inject constructor(
    private val speechCaptureManager: SpeechCaptureManager,
    private val voiceIntentParser: VoiceIntentParser,
    private val voiceActionDispatcher: VoiceActionDispatcher
) : VoiceAssistantManager {
    override val captureState: StateFlow<SpeechCaptureState> = speechCaptureManager.captureState

    override suspend fun handleTranscript(transcript: String, context: VoiceContext): VoiceDispatchResult {
        speechCaptureManager.submitTranscript(transcript)
        return voiceActionDispatcher.dispatch(voiceIntentParser.parse(transcript), context)
    }

    override fun startListening() {
        speechCaptureManager.startListening()
    }

    override fun stopListening() {
        speechCaptureManager.stopListening()
    }
}
