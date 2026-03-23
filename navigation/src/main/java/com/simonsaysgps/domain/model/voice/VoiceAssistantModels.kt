package com.simonsaysgps.domain.model.voice

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.explore.ExploreCategory

sealed interface SpeechCaptureState {
    data object Idle : SpeechCaptureState
    data object Listening : SpeechCaptureState
    data class TranscriptAvailable(val transcript: String) : SpeechCaptureState
    data class Error(val message: String) : SpeechCaptureState
}

data class VoiceContext(
    val currentLocation: Coordinate? = null,
    val selectedPlace: PlaceResult? = null,
    val navigationActive: Boolean = false
)

sealed interface VoiceIntent {
    data class SearchOnRoute(val query: String) : VoiceIntent
    data class Explore(val category: ExploreCategory) : VoiceIntent
    data class CrowdReport(val type: CrowdReportType, val note: String? = null, val confidence: Float = 0.75f) : VoiceIntent
    data object ReviewCurrentPlace : VoiceIntent
    data class ReviewCleanup(val option: ReviewCleanupOption) : VoiceIntent
    data class Soundtrack(val vibe: String) : VoiceIntent
    data class Confirmation(val accepted: Boolean) : VoiceIntent
    data object Unknown : VoiceIntent
}

enum class CrowdReportType(val label: String) {
    SPEED_TRAP("Speed trap / police"),
    TRAFFIC("Traffic congestion"),
    ACCIDENT("Accident"),
    DISABLED_VEHICLE("Disabled vehicle"),
    POTHOLE("Pothole / road hazard"),
    ROADWORK("Roadwork / closure"),
    CHECKPOINT("Checkpoint"),
    SCENIC_ATTRACTION("Scenic / cool attraction"),
    LOCAL_TIP("Local tip / awesome thing")
}

enum class CrowdReportStatus {
    DRAFT,
    SUBMITTED,
    NEEDS_REVIEW,
    DISMISSED
}

data class CrowdReport(
    val id: String,
    val timestampEpochMillis: Long,
    val location: Coordinate?,
    val type: CrowdReportType,
    val transcriptNote: String?,
    val confidence: Float,
    val userConfirmed: Boolean,
    val status: CrowdReportStatus,
    val moderationNotes: String? = null
)

enum class ReviewCleanupOption(val label: String) {
    KEEP_AS_IS("Keep as-is"),
    CLEAN_UP_GRAMMAR("Clean up grammar"),
    MAKE_SHORTER("Make shorter"),
    MAKE_FUNNIER("Make funnier"),
    MAKE_MORE_FACTUAL("Make more factual")
}

enum class ReviewDraftStatus {
    IDLE,
    DRAFTING,
    READY_FOR_CLEANUP,
    READY_FOR_APPROVAL,
    APPROVED
}

data class ReviewDraft(
    val id: String,
    val place: PlaceResult?,
    val rawTranscript: String = "",
    val cleanedSuggestion: String? = null,
    val finalApprovedText: String? = null,
    val selectedCleanupOption: ReviewCleanupOption = ReviewCleanupOption.KEEP_AS_IS,
    val status: ReviewDraftStatus = ReviewDraftStatus.IDLE
)

data class SoundtrackIntent(
    val vibe: String,
    val providerHint: String? = null,
    val safeForDriving: Boolean = true
)

data class SoundtrackResult(
    val providerName: String,
    val message: String,
    val deepLink: String? = null,
    val launched: Boolean = false
)

data class VoiceAssistantSettings(
    val enabled: Boolean = true,
    val handsFreeReportingEnabled: Boolean = true,
    val voiceConfirmationRequired: Boolean = true,
    val aiCleanupOptIn: Boolean = false,
    val soundtrackIntegrationEnabled: Boolean = false,
    val spokenConfirmationsEnabled: Boolean = true
)
