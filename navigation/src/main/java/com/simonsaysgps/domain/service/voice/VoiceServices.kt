package com.simonsaysgps.domain.service.voice

import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.SoundtrackIntent
import com.simonsaysgps.domain.model.voice.SoundtrackResult
import com.simonsaysgps.domain.model.voice.SpeechCaptureState
import com.simonsaysgps.domain.model.voice.VoiceContext
import com.simonsaysgps.domain.model.voice.VoiceIntent
import kotlinx.coroutines.flow.StateFlow

interface SpeechCaptureManager {
    val captureState: StateFlow<SpeechCaptureState>
    fun startListening()
    fun stopListening()
    fun submitTranscript(transcript: String)
}

interface VoiceIntentParser {
    fun parse(transcript: String): VoiceIntent
}

interface ProseCleanupService {
    suspend fun cleanup(rawText: String, option: ReviewCleanupOption): String?
}

interface MusicIntentProvider {
    suspend fun handle(intent: SoundtrackIntent): SoundtrackResult
}

sealed interface VoiceDispatchResult {
    data class Search(val query: String, val onMyWay: Boolean, val spokenConfirmation: String) : VoiceDispatchResult
    data class Explore(val category: ExploreCategory, val spokenConfirmation: String) : VoiceDispatchResult
    data class ReportStaged(val spokenConfirmation: String) : VoiceDispatchResult
    data class ReportSubmitted(val spokenConfirmation: String) : VoiceDispatchResult
    data class ReviewStarted(val spokenConfirmation: String) : VoiceDispatchResult
    data class ReviewUpdated(val spokenConfirmation: String) : VoiceDispatchResult
    data class SoundtrackQueued(val result: SoundtrackResult) : VoiceDispatchResult
    data class NoOp(val spokenConfirmation: String) : VoiceDispatchResult
}

interface VoiceActionDispatcher {
    suspend fun dispatch(intent: VoiceIntent, context: VoiceContext): VoiceDispatchResult
}

interface VoiceAssistantManager {
    val captureState: StateFlow<SpeechCaptureState>
    suspend fun handleTranscript(transcript: String, context: VoiceContext): VoiceDispatchResult
    fun startListening()
    fun stopListening()
}
