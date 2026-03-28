package com.simonsaysgps.service.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Singleton
class AndroidSpeechCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechCaptureManager {
    private val _captureState = MutableStateFlow<SpeechCaptureState>(SpeechCaptureState.Idle)
    override val captureState: StateFlow<SpeechCaptureState> = _captureState.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    override fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _captureState.value = SpeechCaptureState.Error("Microphone permission is required before Simon can listen.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _captureState.value = SpeechCaptureState.Error("Android speech recognition is unavailable on this device. You can still type commands below.")
            return
        }
        _captureState.value = SpeechCaptureState.Listening
        mainHandler.post {
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { created ->
                created.setRecognitionListener(SimonRecognitionListener())
                speechRecognizer = created
            }
            runCatching {
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Simon what you need")
                    }
                )
            }.onFailure {
                _captureState.value = SpeechCaptureState.Error("Simon couldn't start speech recognition. You can type the command instead.")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            if (_captureState.value is SpeechCaptureState.Listening) {
                _captureState.value = SpeechCaptureState.Idle
            }
        }
    }

    override fun submitTranscript(transcript: String) {
        _captureState.value = if (transcript.isBlank()) {
            SpeechCaptureState.Error("Simon didn't catch that. Try a short, direct command.")
        } else {
            SpeechCaptureState.TranscriptAvailable(transcript.trim())
        }
    }

    private inner class SimonRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _captureState.value = SpeechCaptureState.Listening
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            _captureState.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechCaptureState.Error("Simon didn't catch a clear command. Try again or type it below.")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechCaptureState.Error("Microphone permission was denied. Grant it to use voice capture.")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> SpeechCaptureState.Error("Speech recognition hit a network problem. You can still type the command manually.")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechCaptureState.Error("Speech recognition is already busy. Pause a moment and try again.")
                else -> SpeechCaptureState.Error("Speech recognition stopped unexpectedly. You can retry or type the command.")
            }
        }

        override fun onResults(results: Bundle?) {
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            submitTranscript(transcript)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (transcript.isNotBlank()) {
                _captureState.value = SpeechCaptureState.TranscriptAvailable(transcript.trim())
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

@Singleton
class RuleBasedVoiceIntentParser @Inject constructor() : VoiceIntentParser {
    override fun parse(transcript: String): VoiceIntent {
        val normalized = transcript
            .lowercase()
            .replace(Regex("^[\\s,.;:!?-]*simon( says)?[\\s,.;:!?-]*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return VoiceIntent.Unknown
        if (normalized in setOf("yes", "confirm", "submit it", "send it", "looks good", "go ahead")) return VoiceIntent.Confirmation(true)
        if (normalized in setOf("no", "cancel", "never mind", "dismiss", "don't send it")) return VoiceIntent.Confirmation(false)
        if (
            normalized.contains("leave a review") ||
            normalized.contains("leave review") ||
            normalized.contains("review this place") ||
            normalized.contains("review current place")
        ) return VoiceIntent.ReviewCurrentPlace
        if (normalized.contains("clean up grammar")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.CLEAN_UP_GRAMMAR)
        if (normalized.contains("make shorter")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.MAKE_SHORTER)
        if (normalized.contains("make funnier")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.MAKE_FUNNIER)
        if (normalized.contains("more factual")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.MAKE_MORE_FACTUAL)
        if (normalized.contains("keep as is") || normalized.contains("keep as-is")) return VoiceIntent.ReviewCleanup(ReviewCleanupOption.KEEP_AS_IS)
        if (normalized.contains("playlist") || normalized.contains("soundtrack") || normalized.contains("music")) {
            val vibe = normalized.substringBefore("playlist")
                .substringBefore("soundtrack")
                .substringBefore("music")
                .removePrefix("make me")
                .removePrefix("play me")
                .removePrefix("queue")
                .trim()
                .removePrefix("a ")
                .removePrefix("an ")
                .ifBlank { "road trip" }
            return VoiceIntent.Soundtrack(vibe)
        }
        val reportType = when {
            normalized.contains("speed trap") || normalized.contains("police") -> CrowdReportType.SPEED_TRAP
            normalized.contains("traffic") || normalized.contains("jam") || normalized.contains("slowdown") -> CrowdReportType.TRAFFIC
            normalized.contains("accident") || normalized.contains("crash") -> CrowdReportType.ACCIDENT
            normalized.contains("disabled vehicle") -> CrowdReportType.DISABLED_VEHICLE
            normalized.contains("pothole") || normalized.contains("road hazard") || normalized.contains("hazard") -> CrowdReportType.POTHOLE
            normalized.contains("roadwork") || normalized.contains("construction") || normalized.contains("closure") -> CrowdReportType.ROADWORK
            normalized.contains("checkpoint") -> CrowdReportType.CHECKPOINT
            normalized.contains("awesome") || normalized.contains("local tip") -> CrowdReportType.LOCAL_TIP
            normalized.contains("scenic") || normalized.contains("cool attraction") -> CrowdReportType.SCENIC_ATTRACTION
            else -> null
        }
        if ((normalized.startsWith("report") || normalized.contains("there is") || normalized.contains("there's")) && reportType != null) {
            val note = normalized
                .removePrefix("report")
                .replace("there is", "")
                .replace("there's", "")
                .trim()
                .takeIf { it.isNotBlank() && !it.equals(reportType.label.lowercase(), ignoreCase = true) }
            val confidence = when {
                normalized.contains("speed trap") || normalized.contains("police") || normalized.contains("traffic") -> 0.9f
                else -> 0.8f
            }
            return VoiceIntent.CrowdReport(reportType, note = note, confidence = confidence)
        }
        if (normalized.contains("on my way")) {
            val routeQuery = normalized
                .substringBefore("on my way")
                .replace(Regex("^(find|show me|get me|look for|take me to)\\s+"), "")
                .replace(Regex("\\b(something|somewhere|anything|a place|place)\\b"), "")
                .trim()
            if (routeQuery.isNotBlank()) return VoiceIntent.SearchOnRoute(routeQuery)
            return VoiceIntent.Explore(ExploreCategory.ON_MY_WAY)
        }
        val exploreCategory = when {
            normalized.contains("fun") || normalized.contains("something to do") -> ExploreCategory.FUN
            normalized.contains("good to eat") || normalized.contains("somewhere to eat") || normalized.contains("food") || normalized.contains("coffee") -> ExploreCategory.DELICIOUS
            normalized.contains("open now") -> ExploreCategory.OPEN_NOW
            normalized.contains("never been") -> ExploreCategory.NEVER_BEEN
            normalized.contains("quiet") || normalized.contains("peaceful") -> ExploreCategory.QUIET
            normalized.contains("outdoors") || normalized.contains("outside") || normalized.contains("park") -> ExploreCategory.OUTDOORS
            normalized.contains("important") || normalized.contains("landmark") || normalized.contains("museum") -> ExploreCategory.IMPORTANT
            normalized.contains("close to home") || normalized.contains("near home") -> ExploreCategory.CLOSE_TO_HOME
            normalized.contains("special") || normalized.contains("memorable") -> ExploreCategory.SPECIAL
            normalized.contains("new place") || normalized.contains("something new") -> ExploreCategory.NEW
            normalized.contains("shop") || normalized.contains("shopping") -> ExploreCategory.I_CAN_SHOP
            normalized.contains("learn") || normalized.contains("educational") -> ExploreCategory.I_CAN_LEARN
            normalized.contains("kid") || normalized.contains("family") -> ExploreCategory.GOOD_FOR_KIDS
            normalized.contains("sale") || normalized.contains("deal") -> ExploreCategory.HAVING_A_SALE
            else -> null
        }
        if (exploreCategory != null && (
                normalized.contains("take me") ||
                    normalized.contains("show me") ||
                    normalized.contains("somewhere") ||
                    normalized.contains("find") ||
                    normalized.contains("explore")
                )
        ) return VoiceIntent.Explore(exploreCategory)
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
                category = intent.category,
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
                val selectedPlace = context.selectedPlace
                reviewDraftRepository.startDraft(
                    ReviewDraft(
                        id = "review-${System.currentTimeMillis()}",
                        place = selectedPlace
                    )
                )
                VoiceDispatchResult.ReviewStarted(
                    spokenConfirmation = if (selectedPlace != null) {
                        "Ready to draft a review for ${selectedPlace.name}. Dictate your note when you're ready."
                    } else {
                        "Ready to draft a quick review. Pick a place first for the final save target."
                    }
                )
            }

            is VoiceIntent.ReviewCleanup -> {
                val current = reviewDraftRepository.activeDraft.first()
                if (current == null) {
                    return VoiceDispatchResult.NoOp("Start a review draft first, then Simon can help clean it up.")
                }
                val cleaned = proseCleanupService.cleanup(current.rawTranscript, intent.option)
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
