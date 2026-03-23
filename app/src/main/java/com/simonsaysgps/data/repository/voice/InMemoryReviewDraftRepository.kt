package com.simonsaysgps.data.repository.voice

import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import com.simonsaysgps.domain.model.voice.ReviewDraftStatus
import com.simonsaysgps.domain.repository.voice.ReviewDraftRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class InMemoryReviewDraftRepository @Inject constructor() : ReviewDraftRepository {
    private val draftState = MutableStateFlow<ReviewDraft?>(null)

    override val activeDraft: Flow<ReviewDraft?> = draftState.asStateFlow()

    override suspend fun startDraft(draft: ReviewDraft) {
        draftState.value = draft.copy(status = ReviewDraftStatus.DRAFTING)
    }

    override suspend fun updateRawTranscript(transcript: String) {
        val current = draftState.value ?: return
        draftState.value = current.copy(
            rawTranscript = transcript,
            status = ReviewDraftStatus.READY_FOR_CLEANUP,
            finalApprovedText = null
        )
    }

    override suspend fun applyCleanupSuggestion(option: ReviewCleanupOption, suggestion: String?) {
        val current = draftState.value ?: return
        draftState.value = current.copy(
            selectedCleanupOption = option,
            cleanedSuggestion = suggestion,
            status = ReviewDraftStatus.READY_FOR_APPROVAL
        )
    }

    override suspend fun approveFinalText(text: String) {
        val current = draftState.value ?: return
        draftState.value = current.copy(finalApprovedText = text, status = ReviewDraftStatus.APPROVED)
    }

    override suspend fun clearDraft() {
        draftState.value = null
    }
}
