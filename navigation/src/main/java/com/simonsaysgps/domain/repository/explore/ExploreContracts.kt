package com.simonsaysgps.domain.repository.explore

import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreEventInfo
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreRepositorySnapshot
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary

interface ExploreRepository {
    suspend fun fetch(query: ExploreQuery): ExploreRepositorySnapshot
}

interface PlaceDiscoveryProvider {
    val providerId: String
    suspend fun discoverPlaces(query: ExploreQuery): List<ExploreCandidate>
}

interface PlaceDetailsProvider {
    val providerId: String
    suspend fun enrichPlaces(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, ExploreCandidate>
}

interface ReviewProvider {
    val providerId: String
    suspend fun reviews(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExploreReviewSourceSummary>>
}

interface EventProvider {
    val providerId: String
    suspend fun events(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExploreEventInfo>>
}

interface PromotionSignalProvider {
    val providerId: String
    suspend fun promotions(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExplorePromotionInfo>>
}

interface UserVisitHistoryProvider {
    suspend fun enrichPlaces(candidates: List<ExploreCandidate>): Map<String, ExploreCandidate>
}

interface ExploreProviderHealthReporter {
    fun available(providerName: String, detail: String): ExploreProviderStatus = ExploreProviderStatus(providerName, true, detail)
    fun unavailable(providerName: String, detail: String): ExploreProviderStatus = ExploreProviderStatus(providerName, false, detail)
}
