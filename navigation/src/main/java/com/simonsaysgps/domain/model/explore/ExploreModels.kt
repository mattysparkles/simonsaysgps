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
    val closeToHomeRadiusMiles: Int = 8,
    val visitHistoryEnabled: Boolean = true,
    val visitHistoryRetentionDays: Int = 180,
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

data class ExploreSourceAttribution(
    val provider: String,
    val label: String = provider,
    val verified: Boolean = true,
    val termsLimitedToSummary: Boolean = false,
    val debugDetail: String? = null
)

data class ExploreProviderLink(
    val provider: String,
    val externalId: String
)

data class ExploreConfidenceSignal(
    val label: String,
    val confidence: Float,
    val inferred: Boolean,
    val detail: String,
    val source: ExploreSourceAttribution
)

data class ExploreReviewSourceSummary(
    val provider: String,
    val providerLabel: String,
    val averageRating: Double?,
    val reviewCount: Int,
    val summary: String? = null,
    val internal: Boolean = false,
    val attribution: ExploreSourceAttribution = ExploreSourceAttribution(provider = provider, label = providerLabel, verified = !internal),
    val confidence: Float = if (internal) 0.95f else 0.8f
)

data class ExploreReviewSummary(
    val averageRating: Double,
    val totalCount: Int,
    val internalAverageRating: Double? = null,
    val internalCount: Int = 0,
    val summary: String? = null,
    val sources: List<ExploreReviewSourceSummary> = emptyList()
) {
    val internalSources: List<ExploreReviewSourceSummary> = sources.filter { it.internal }
    val thirdPartySources: List<ExploreReviewSourceSummary> = sources.filterNot { it.internal }
}

enum class ExploreEventTiming {
    HAPPENING_NOW,
    STARTING_SOON,
    UPCOMING,
    ALL_DAY
}

data class ExploreEventInfo(
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val summary: String? = null,
    val timing: ExploreEventTiming = ExploreEventTiming.UPCOMING,
    val attribution: ExploreSourceAttribution,
    val confidence: Float = 0.8f
)

data class ExplorePromotionInfo(
    val summary: String,
    val confidence: Float,
    val source: String,
    val attribution: ExploreSourceAttribution = ExploreSourceAttribution(provider = source, label = source, verified = confidence >= 0.9f),
    val inferred: Boolean = confidence < 0.9f
)

data class ExploreCandidate(
    val id: String,
    val name: String,
    val typeLabel: String,
    val address: String,
    val coordinate: Coordinate,
    val facets: Set<ExploreFacet>,
    val openNow: Boolean? = null,
    val phoneNumber: String? = null,
    val websiteUrl: String? = null,
    val reviewSummary: ExploreReviewSummary? = null,
    val eventSignals: List<ExploreEventInfo> = emptyList(),
    val promotionSignals: List<ExplorePromotionInfo> = emptyList(),
    val visitedBefore: Boolean = false,
    val lastVisitedEpochMillis: Long? = null,
    val sourceConfidence: Float = 0.65f,
    val accessible: Boolean? = null,
    val kidFriendly: Boolean? = null,
    val quietScore: Float? = null,
    val alcoholFocused: Boolean = false,
    val adultOriented: Boolean = false,
    val recentlyOpenedOrTrending: Boolean = false,
    val sourceAttributions: List<ExploreSourceAttribution> = emptyList(),
    val providerLinks: List<ExploreProviderLink> = emptyList(),
    val confidenceSignals: List<ExploreConfidenceSignal> = emptyList(),
    val whyChosenHints: List<String> = emptyList()
) {
    val primaryEvent: ExploreEventInfo? = eventSignals.minByOrNull { it.startEpochMillis }
    val primaryPromotion: ExplorePromotionInfo? = promotionSignals.maxByOrNull { it.confidence }
}

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
    val estimatedDetourMinutes: Int? = null,
    val homeDistanceMeters: Double? = null,
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
