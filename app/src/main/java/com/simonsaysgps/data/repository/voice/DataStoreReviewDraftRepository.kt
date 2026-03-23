package com.simonsaysgps.data.repository.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import com.simonsaysgps.domain.model.voice.ReviewDraftStatus
import com.simonsaysgps.domain.repository.voice.ReviewDraftRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reviewDraftDataStore by preferencesDataStore(name = "simonsays_voice_review_drafts")

@Singleton
class DataStoreReviewDraftRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : ReviewDraftRepository {
    private val adapter: JsonAdapter<List<StoredReviewDraft>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredReviewDraft::class.java)
    )

    override val drafts: Flow<List<ReviewDraft>> = context.reviewDraftDataStore.data.map { prefs ->
        ReviewDraftStorage.decode(prefs[REVIEW_DRAFTS], adapter)
    }

    override val activeDraft: Flow<ReviewDraft?> = drafts.map { draftList ->
        draftList.firstOrNull { it.status != ReviewDraftStatus.APPROVED }
    }

    override suspend fun startDraft(draft: ReviewDraft) {
        updateDrafts { existing ->
            listOf(
                draft.copy(
                    status = ReviewDraftStatus.DRAFTING,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            ) + existing.filter { it.status == ReviewDraftStatus.APPROVED }
        }
    }

    override suspend fun updateRawTranscript(transcript: String) {
        updateActiveDraft { current ->
            current.copy(
                rawTranscript = transcript,
                cleanedSuggestion = null,
                finalApprovedText = null,
                status = ReviewDraftStatus.READY_FOR_CLEANUP,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }

    override suspend fun applyCleanupSuggestion(option: ReviewCleanupOption, suggestion: String?) {
        updateActiveDraft { current ->
            current.copy(
                selectedCleanupOption = option,
                cleanedSuggestion = suggestion,
                status = ReviewDraftStatus.READY_FOR_APPROVAL,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }

    override suspend fun approveFinalText(text: String) {
        updateActiveDraft { current ->
            current.copy(
                finalApprovedText = text,
                status = ReviewDraftStatus.APPROVED,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }

    override suspend fun clearDraft() {
        updateDrafts { drafts -> drafts.filter { it.status == ReviewDraftStatus.APPROVED } }
    }

    private suspend fun updateActiveDraft(transform: (ReviewDraft) -> ReviewDraft) {
        updateDrafts { drafts ->
            val active = drafts.firstOrNull { it.status != ReviewDraftStatus.APPROVED } ?: return@updateDrafts drafts
            drafts.map { draft -> if (draft.id == active.id) transform(draft) else draft }
                .sortedByDescending(ReviewDraft::updatedAtEpochMillis)
        }
    }

    private suspend fun updateDrafts(transform: (List<ReviewDraft>) -> List<ReviewDraft>) {
        context.reviewDraftDataStore.edit { prefs ->
            val existing = ReviewDraftStorage.decode(prefs[REVIEW_DRAFTS], adapter)
            prefs[REVIEW_DRAFTS] = ReviewDraftStorage.encode(transform(existing), adapter)
        }
    }

    private companion object {
        val REVIEW_DRAFTS = stringPreferencesKey("review_drafts")
    }
}

internal object ReviewDraftStorage {
    fun encode(drafts: List<ReviewDraft>, adapter: JsonAdapter<List<StoredReviewDraft>>): String =
        adapter.toJson(drafts.map(StoredReviewDraft::fromDomain))

    fun decode(rawValue: String?, adapter: JsonAdapter<List<StoredReviewDraft>>): List<ReviewDraft> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(rawValue)?.map(StoredReviewDraft::toDomain).orEmpty()
        }.getOrDefault(emptyList())
    }
}

internal data class StoredReviewDraft(
    val id: String,
    val placeId: String?,
    val placeName: String?,
    val placeAddress: String?,
    val placeLatitude: Double?,
    val placeLongitude: Double?,
    val rawTranscript: String,
    val cleanedSuggestion: String?,
    val finalApprovedText: String?,
    val selectedCleanupOption: String,
    val status: String,
    val updatedAtEpochMillis: Long
) {
    fun toDomain() = ReviewDraft(
        id = id,
        place = if (placeId != null && placeName != null && placeAddress != null && placeLatitude != null && placeLongitude != null) {
            PlaceResult(
                id = placeId,
                name = placeName,
                fullAddress = placeAddress,
                coordinate = Coordinate(placeLatitude, placeLongitude)
            )
        } else {
            null
        },
        rawTranscript = rawTranscript,
        cleanedSuggestion = cleanedSuggestion,
        finalApprovedText = finalApprovedText,
        selectedCleanupOption = ReviewCleanupOption.valueOf(selectedCleanupOption),
        status = ReviewDraftStatus.valueOf(status),
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    companion object {
        fun fromDomain(draft: ReviewDraft) = StoredReviewDraft(
            id = draft.id,
            placeId = draft.place?.id,
            placeName = draft.place?.name,
            placeAddress = draft.place?.fullAddress,
            placeLatitude = draft.place?.coordinate?.latitude,
            placeLongitude = draft.place?.coordinate?.longitude,
            rawTranscript = draft.rawTranscript,
            cleanedSuggestion = draft.cleanedSuggestion,
            finalApprovedText = draft.finalApprovedText,
            selectedCleanupOption = draft.selectedCleanupOption.name,
            status = draft.status.name,
            updatedAtEpochMillis = draft.updatedAtEpochMillis
        )
    }
}
