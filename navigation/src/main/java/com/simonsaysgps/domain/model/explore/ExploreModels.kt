package com.simonsaysgps.domain.model.explore

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.Route

enum class ExploreSuggestionCount { AUTO_PICK, THREE_CHOICES }

enum class QuietPreferenceStrictness { RELAXED, BALANCED, STRICT }

enum class AccessiblePlacesPreference { FLEXIBLE, PREFER_ACCESSIBLE, ACCESSIBLE_ONLY }

enum class ExploreFacet {
    FOOD,
    DRINK,
    ENTERTAINMENT,
    ACTIVITY,
    PARK,
    OUTDOORS,
    SHOPPING,
    LEARNING,
    KIDS,
    QUIET,
    IMPORTANT,
    SCENIC,
    HISTORY,
    GOVERNMENT,
    LANDMARK,
    SALE,
    TRENDING,
    NEW,
    ACCESSIBLE,
    ALCOHOL,
    ADULT,
    NATURE
}

data class ExploreSettings(
    val defaultRadiusMiles: Int = 10,
    val requireOpenNowByDefault: Boolean = true,
    val suggestionCount: ExploreSuggestionCount = ExploreSuggestionCount.THREE_CHOICES,
    val allowRouteDetoursWhileNavigating: Boolean = true,
    val maxDetourDistanceMiles: Double = 5.0,
    val maxDetourMinutes: Int = 12,
    val useEventDataWhenAvailable: Boolean = true,
    val useInternalReviewsFirst: Boolean = true,
    val includeThirdPartyReviewSummariesWhenAvailable: Boolean = true,
    val homeLabel: String = "",
    val homeCoordinate: Coordinate? = null,
    val surpriseMeWeight: Float = 0.2f,
    val kidFriendlyOnly: Boolean = false,
    val quietPreferenceStrictness: QuietPreferenceStrictness = QuietPreferenceStrictness.BALANCED,
    val accessiblePlacesPreference: AccessiblePlacesPreference = AccessiblePlacesPreference.PREFER_ACCESSIBLE,
    val avoidAlcoholFocusedVenues: Boolean = true,
    val avoidAdultOrientedVenues: Boolean = true,
    val walkthroughSeen: Boolean = false
)

data class ExploreQuery(
    val category: ExploreCategory,
    val userLocation: Coordinate,
    val activeRoute: Route? = null,
    val nowEpochMillis: Long = System.currentTimeMillis(),
    val settings: ExploreSettings = ExploreSettings()
)

data class ExploreReviewSummary(
    val averageRating: Double,
    val totalCount: Int,
    val internalAverageRating: Double? = null,
    val internalCount: Int = 0,
    val summary: String? = null
)

data class ExploreEventInfo(
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val summary: String? = null
)

data class ExplorePromotionInfo(
    val summary: String,
    val confidence: Float,
    val source: String
)

data class ExploreCandidate(
    val id: String,
    val name: String,
    val typeLabel: String,
    val address: String,
    val coordinate: Coordinate,
    val facets: Set<ExploreFacet>,
    val openNow: Boolean? = null,
    val reviewSummary: ExploreReviewSummary? = null,
    val eventInfo: ExploreEventInfo? = null,
    val promotionInfo: ExplorePromotionInfo? = null,
    val visitedBefore: Boolean = false,
    val lastVisitedEpochMillis: Long? = null,
    val sourceConfidence: Float = 0.65f,
    val accessible: Boolean? = null,
    val kidFriendly: Boolean? = null,
    val quietScore: Float? = null,
    val alcoholFocused: Boolean = false,
    val adultOriented: Boolean = false,
    val recentlyOpenedOrTrending: Boolean = false
)

data class ExploreReason(
    val title: String,
    val detail: String,
    val contribution: Double,
    val confidence: Float
)

data class ExploreResult(
    val candidate: ExploreCandidate,
    val score: Double,
    val confidence: Float,
    val distanceMeters: Double,
    val offRouteDistanceMeters: Double? = null,
    val reasons: List<ExploreReason>,
    val debugBreakdown: Map<String, Double>
) {
    val primaryWhyChosen: String = reasons.maxByOrNull { it.contribution }?.detail
        ?: "A balanced Explore match based on the signals currently available."
}

data class ExploreProviderStatus(
    val provider: String,
    val available: Boolean,
    val detail: String
)

data class ExploreRepositorySnapshot(
    val candidates: List<ExploreCandidate>,
    val providerStatuses: List<ExploreProviderStatus>
)

data class ExploreResponse(
    val results: List<ExploreResult>,
    val providerStatuses: List<ExploreProviderStatus>,
    val autoPicked: Boolean,
    val totalCandidatesConsidered: Int
)
