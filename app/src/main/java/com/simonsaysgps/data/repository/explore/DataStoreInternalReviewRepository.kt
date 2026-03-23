package com.simonsaysgps.data.repository.explore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.InternalReviewModerationStatus
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.simonsaysgps.domain.repository.explore.InternalReviewRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.internalReviewDataStore by preferencesDataStore(name = "simonsays_internal_reviews")

@Singleton
class DataStoreInternalReviewRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : InternalReviewRepository {
    private val adapter: JsonAdapter<List<StoredInternalReview>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredInternalReview::class.java)
    )

    override val localAuthorDisplayName: Flow<String> = context.internalReviewDataStore.data.map { prefs ->
        prefs[AUTHOR_DISPLAY_NAME] ?: DEFAULT_AUTHOR_DISPLAY_NAME
    }

    override fun observeReviews(canonicalPlaceId: String): Flow<List<InternalPlaceReview>> = context.internalReviewDataStore.data.map { prefs ->
        InternalReviewStorage.decode(prefs[INTERNAL_REVIEWS], adapter)
            .filter { it.canonicalPlaceId == canonicalPlaceId }
            .sortedByDescending { it.updatedAtEpochMillis }
    }

    override fun observeOwnReview(canonicalPlaceId: String): Flow<InternalPlaceReview?> = context.internalReviewDataStore.data.map { prefs ->
        val author = prefs[AUTHOR_DISPLAY_NAME] ?: DEFAULT_AUTHOR_DISPLAY_NAME
        InternalReviewStorage.decode(prefs[INTERNAL_REVIEWS], adapter)
            .filter { it.canonicalPlaceId == canonicalPlaceId && it.authorDisplayName == author }
            .maxByOrNull { it.updatedAtEpochMillis }
    }

    override suspend fun upsert(review: InternalPlaceReview) {
        context.internalReviewDataStore.edit { prefs ->
            val existing = InternalReviewStorage.decode(prefs[INTERNAL_REVIEWS], adapter)
            val updated = listOf(review) + existing.filterNot { it.internalReviewId == review.internalReviewId }
            prefs[INTERNAL_REVIEWS] = InternalReviewStorage.encode(updated.sortedByDescending { it.updatedAtEpochMillis }, adapter)
            if (prefs[AUTHOR_DISPLAY_NAME].isNullOrBlank()) {
                prefs[AUTHOR_DISPLAY_NAME] = review.authorDisplayName.ifBlank { DEFAULT_AUTHOR_DISPLAY_NAME }
            }
        }
    }

    override suspend fun remove(reviewId: String) {
        context.internalReviewDataStore.edit { prefs ->
            val updated = InternalReviewStorage.decode(prefs[INTERNAL_REVIEWS], adapter)
                .filterNot { it.internalReviewId == reviewId }
            prefs[INTERNAL_REVIEWS] = InternalReviewStorage.encode(updated, adapter)
        }
    }

    private companion object {
        val INTERNAL_REVIEWS = stringPreferencesKey("internal_reviews")
        val AUTHOR_DISPLAY_NAME = stringPreferencesKey("internal_review_author_display_name")
        const val DEFAULT_AUTHOR_DISPLAY_NAME = "Local driver"
    }
}

internal object InternalReviewStorage {
    fun encode(reviews: List<InternalPlaceReview>, adapter: JsonAdapter<List<StoredInternalReview>>): String =
        adapter.toJson(reviews.map(StoredInternalReview::fromDomain))

    fun decode(rawValue: String?, adapter: JsonAdapter<List<StoredInternalReview>>): List<InternalPlaceReview> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(rawValue)?.map(StoredInternalReview::toDomain).orEmpty()
        }.getOrDefault(emptyList())
    }
}

internal data class StoredInternalReview(
    val internalReviewId: String,
    val canonicalPlaceId: String,
    val authorDisplayName: String,
    val rating: Int,
    val reviewText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val tags: List<String>,
    val visitContext: String? = null,
    val moderationStatus: String,
    val source: String
) {
    fun toDomain() = InternalPlaceReview(
        internalReviewId = internalReviewId,
        canonicalPlaceId = canonicalPlaceId,
        authorDisplayName = authorDisplayName,
        rating = rating,
        reviewText = reviewText,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        tags = tags.mapNotNull { raw -> PlaceReviewTag.entries.firstOrNull { it.name == raw } }.toSet(),
        visitContext = visitContext,
        moderationStatus = runCatching { InternalReviewModerationStatus.valueOf(moderationStatus) }
            .getOrDefault(InternalReviewModerationStatus.VISIBLE),
        source = source
    )

    companion object {
        fun fromDomain(review: InternalPlaceReview) = StoredInternalReview(
            internalReviewId = review.internalReviewId,
            canonicalPlaceId = review.canonicalPlaceId,
            authorDisplayName = review.authorDisplayName,
            rating = review.rating,
            reviewText = review.reviewText,
            createdAtEpochMillis = review.createdAtEpochMillis,
            updatedAtEpochMillis = review.updatedAtEpochMillis,
            tags = review.tags.map { it.name },
            visitContext = review.visitContext,
            moderationStatus = review.moderationStatus.name,
            source = review.source
        )
    }
}
