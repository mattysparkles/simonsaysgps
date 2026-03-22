package com.simonsaysgps.domain.repository.explore

import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreEventInfo
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreRepositorySnapshot
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary

interface ExploreRepository {
    suspend fun fetch(query: ExploreQuery): ExploreRepositorySnapshot
}

interface PlaceDataProvider {
    suspend fun discoverPlaces(query: ExploreQuery): List<ExploreCandidate>
}

interface EventDataProvider {
    suspend fun discoverEvents(query: ExploreQuery): Map<String, ExploreEventInfo>
}

interface UserVisitHistoryProvider {
    suspend fun visitedPlaceIds(): Set<String>
    suspend fun lastVisitedEpochMillis(placeId: String): Long?
}

interface ReviewsProvider {
    suspend fun reviewSummaries(placeIds: List<String>): Map<String, ExploreReviewSummary>
}

interface PromotionsProvider {
    suspend fun promotions(placeIds: List<String>): Map<String, ExplorePromotionInfo>
}

interface ExploreProviderHealthReporter {
    fun available(providerName: String, detail: String): ExploreProviderStatus = ExploreProviderStatus(providerName, true, detail)
    fun unavailable(providerName: String, detail: String): ExploreProviderStatus = ExploreProviderStatus(providerName, false, detail)
}
