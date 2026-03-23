package com.simonsaysgps.domain.repository.explore

import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.PlaceDetailRecord
import com.simonsaysgps.domain.model.explore.ExploreResult
import kotlinx.coroutines.flow.Flow

interface InternalReviewRepository {
    val localAuthorDisplayName: Flow<String>
    fun observeReviews(canonicalPlaceId: String): Flow<List<InternalPlaceReview>>
    fun observeOwnReview(canonicalPlaceId: String): Flow<InternalPlaceReview?>
    suspend fun upsert(review: InternalPlaceReview)
    suspend fun remove(reviewId: String)
}

interface PlaceDetailRepository {
    fun observePlaceDetail(seed: ExploreResult): Flow<PlaceDetailRecord>
}
