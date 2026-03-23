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
import kotlinx.coroutines.flow.map

@Singleton
class InMemoryReviewDraftRepository @Inject constructor() : ReviewDraftRepository {
    private val draftState = MutableStateFlow<List<ReviewDraft>>(emptyList())

    override val drafts: Flow<List<ReviewDraft>> = draftState.asStateFlow()
    override val activeDraft: Flow<ReviewDraft?> = draftState.asStateFlow().map { drafts ->
        drafts.firstOrNull { it.status != ReviewDraftStatus.APPROVED }
    }

    override suspend fun startDraft(draft: ReviewDraft) {
        draftState.value = listOf(
            draft.copy(status = ReviewDraftStatus.DRAFTING, updatedAtEpochMillis = System.currentTimeMillis())
        ) + draftState.value.filter { it.status == ReviewDraftStatus.APPROVED }
    }

    override suspend fun updateRawTranscript(transcript: String) {
        val current = draftState.value.firstOrNull { it.status != ReviewDraftStatus.APPROVED } ?: return
        draftState.value = draftState.value.map { draft ->
            if (draft.id != current.id) {
                draft
            } else {
                draft.copy(
                    rawTranscript = transcript,
                    status = ReviewDraftStatus.READY_FOR_CLEANUP,
                    finalApprovedText = null,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun applyCleanupSuggestion(option: ReviewCleanupOption, suggestion: String?) {
        val current = draftState.value.firstOrNull { it.status != ReviewDraftStatus.APPROVED } ?: return
        draftState.value = draftState.value.map { draft ->
            if (draft.id != current.id) {
                draft
            } else {
                draft.copy(
                    selectedCleanupOption = option,
                    cleanedSuggestion = suggestion,
                    status = ReviewDraftStatus.READY_FOR_APPROVAL,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun approveFinalText(text: String) {
        val current = draftState.value.firstOrNull { it.status != ReviewDraftStatus.APPROVED } ?: return
        draftState.value = draftState.value.map { draft ->
            if (draft.id != current.id) {
                draft
            } else {
                draft.copy(
                    finalApprovedText = text,
                    status = ReviewDraftStatus.APPROVED,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun clearDraft() {
        draftState.value = draftState.value.filter { it.status == ReviewDraftStatus.APPROVED }
    }
}
