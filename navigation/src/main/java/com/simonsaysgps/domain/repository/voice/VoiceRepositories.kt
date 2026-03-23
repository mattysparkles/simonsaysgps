package com.simonsaysgps.domain.repository.voice

import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import kotlinx.coroutines.flow.Flow

interface CrowdReportRepository {
    val reports: Flow<List<CrowdReport>>
    val pendingReport: Flow<CrowdReport?>
    suspend fun stage(report: CrowdReport)
    suspend fun confirmPending()
    suspend fun dismissPending(reason: String? = null)
}

interface ReviewDraftRepository {
    val drafts: Flow<List<ReviewDraft>>
    val activeDraft: Flow<ReviewDraft?>
    suspend fun startDraft(draft: ReviewDraft)
    suspend fun updateRawTranscript(transcript: String)
    suspend fun applyCleanupSuggestion(option: ReviewCleanupOption, suggestion: String?)
    suspend fun approveFinalText(text: String)
    suspend fun clearDraft()
}
