package com.simonsaysgps.data.explore

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreEventInfo
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary
import com.simonsaysgps.domain.repository.RecentDestinationRepository
import com.simonsaysgps.domain.repository.explore.EventDataProvider
import com.simonsaysgps.domain.repository.explore.PlaceDataProvider
import com.simonsaysgps.domain.repository.explore.PromotionsProvider
import com.simonsaysgps.domain.repository.explore.ReviewsProvider
import com.simonsaysgps.domain.repository.explore.UserVisitHistoryProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DemoPlaceDataProvider @Inject constructor() : PlaceDataProvider {
    override suspend fun discoverPlaces(query: ExploreQuery): List<ExploreCandidate> {
        val origin = query.userLocation
        return catalog(origin)
    }

    private fun catalog(origin: Coordinate): List<ExploreCandidate> = listOf(
        candidate(origin, "corner-bistro", "Corner Bistro", "Restaurant", 0.0030, 0.0008, setOf(ExploreFacet.FOOD, ExploreFacet.DRINK), openNow = true, quietScore = 0.32f),
        candidate(origin, "mini-golf", "Glow Mini Golf", "Activity venue", 0.0040, 0.0020, setOf(ExploreFacet.ACTIVITY, ExploreFacet.ENTERTAINMENT, ExploreFacet.KIDS), openNow = true, quietScore = 0.25f),
        candidate(origin, "library", "Neighborhood Library", "Library", 0.0020, -0.0015, setOf(ExploreFacet.LEARNING, ExploreFacet.QUIET, ExploreFacet.IMPORTANT), openNow = true, quietScore = 0.92f),
        candidate(origin, "trail", "River Walk Trail", "Trail", 0.0070, -0.0020, setOf(ExploreFacet.OUTDOORS, ExploreFacet.PARK, ExploreFacet.NATURE, ExploreFacet.SCENIC), openNow = true, quietScore = 0.95f),
        candidate(origin, "museum", "City History Museum", "Museum", -0.0020, 0.0030, setOf(ExploreFacet.LEARNING, ExploreFacet.HISTORY, ExploreFacet.IMPORTANT, ExploreFacet.LANDMARK), openNow = true, quietScore = 0.71f),
        candidate(origin, "market", "Main Street Market", "Shopping district", 0.0015, 0.0045, setOf(ExploreFacet.SHOPPING, ExploreFacet.TRENDING), openNow = true, quietScore = 0.35f),
        candidate(origin, "play-space", "Kids Play Space", "Indoor playground", -0.0035, 0.0015, setOf(ExploreFacet.KIDS, ExploreFacet.ACTIVITY, ExploreFacet.ENTERTAINMENT), openNow = true, quietScore = 0.30f),
        candidate(origin, "lookout", "Sunset Lookout", "Scenic overlook", 0.0065, 0.0030, setOf(ExploreFacet.OUTDOORS, ExploreFacet.SCENIC, ExploreFacet.QUIET, ExploreFacet.LANDMARK, ExploreFacet.NATURE), openNow = true, quietScore = 0.97f),
        candidate(origin, "civic", "City Hall", "Civic landmark", -0.0040, -0.0020, setOf(ExploreFacet.GOVERNMENT, ExploreFacet.IMPORTANT, ExploreFacet.LANDMARK), openNow = false, quietScore = 0.55f, accessible = true),
        candidate(origin, "thrift", "Thrift + Outlet Row", "Outlet strip", -0.0010, 0.0055, setOf(ExploreFacet.SHOPPING, ExploreFacet.SALE, ExploreFacet.TRENDING), openNow = true, quietScore = 0.42f),
        candidate(origin, "botanical", "Botanical Garden", "Garden", 0.0055, -0.0040, setOf(ExploreFacet.OUTDOORS, ExploreFacet.LEARNING, ExploreFacet.SCENIC, ExploreFacet.QUIET, ExploreFacet.NATURE), openNow = true, quietScore = 0.91f, recentlyOpenedOrTrending = true),
        candidate(origin, "nightlife", "Nightlife Strip", "Bar district", -0.0050, 0.0005, setOf(ExploreFacet.DRINK, ExploreFacet.ENTERTAINMENT, ExploreFacet.ALCOHOL), openNow = true, quietScore = 0.1f, alcoholFocused = true, kidFriendly = false, accessible = false)
    )

    private fun candidate(
        origin: Coordinate,
        id: String,
        name: String,
        typeLabel: String,
        latOffset: Double,
        lonOffset: Double,
        facets: Set<ExploreFacet>,
        openNow: Boolean,
        quietScore: Float,
        accessible: Boolean = true,
        kidFriendly: Boolean = true,
        alcoholFocused: Boolean = false,
        recentlyOpenedOrTrending: Boolean = false
    ) = ExploreCandidate(
        id = id,
        name = name,
        typeLabel = typeLabel,
        address = "${(100..999).random()} Explore Ave",
        coordinate = Coordinate(origin.latitude + latOffset, origin.longitude + lonOffset),
        facets = facets,
        openNow = openNow,
        sourceConfidence = 0.82f,
        accessible = accessible || ExploreFacet.ACCESSIBLE in facets,
        kidFriendly = kidFriendly,
        quietScore = quietScore,
        alcoholFocused = alcoholFocused,
        recentlyOpenedOrTrending = recentlyOpenedOrTrending
    )
}

@Singleton
class DemoEventDataProvider @Inject constructor() : EventDataProvider {
    override suspend fun discoverEvents(query: ExploreQuery): Map<String, ExploreEventInfo> {
        if (!query.settings.useEventDataWhenAvailable) return emptyMap()
        val now = query.nowEpochMillis
        return mapOf(
            "museum" to ExploreEventInfo(
                title = "Late-opening history talk",
                startEpochMillis = now + 45 * 60 * 1000,
                endEpochMillis = now + 2 * 60 * 60 * 1000,
                summary = "Docent-led evening program"
            ),
            "market" to ExploreEventInfo(
                title = "Local maker pop-up",
                startEpochMillis = now + 20 * 60 * 1000,
                endEpochMillis = now + 3 * 60 * 60 * 1000,
                summary = "Rotating artisans and small brands"
            )
        )
    }
}

@Singleton
class DemoReviewsProvider @Inject constructor() : ReviewsProvider {
    override suspend fun reviewSummaries(placeIds: List<String>): Map<String, ExploreReviewSummary> {
        val seedData = mapOf(
            "corner-bistro" to ExploreReviewSummary(4.7, 428, internalAverageRating = 4.8, internalCount = 19, summary = "Comfort food with fast service."),
            "mini-golf" to ExploreReviewSummary(4.4, 191, internalAverageRating = 4.6, internalCount = 8, summary = "Fun date-night and family stop."),
            "library" to ExploreReviewSummary(4.8, 88, internalAverageRating = 4.9, internalCount = 5, summary = "Quiet reading rooms and friendly staff."),
            "trail" to ExploreReviewSummary(4.9, 222, internalAverageRating = 4.7, internalCount = 9, summary = "Reliable scenic walk close to town."),
            "museum" to ExploreReviewSummary(4.6, 300, internalAverageRating = 4.5, internalCount = 6, summary = "Great exhibits and rotating programs."),
            "market" to ExploreReviewSummary(4.5, 275, internalAverageRating = 4.4, internalCount = 7, summary = "Good variety of local shops."),
            "play-space" to ExploreReviewSummary(4.7, 162, internalAverageRating = 4.9, internalCount = 12, summary = "Parents mention clean play areas."),
            "lookout" to ExploreReviewSummary(4.9, 154, internalAverageRating = 4.8, internalCount = 4, summary = "Worth it near sunset."),
            "civic" to ExploreReviewSummary(4.2, 49, internalAverageRating = 4.1, internalCount = 2, summary = "Interesting architecture."),
            "thrift" to ExploreReviewSummary(4.3, 133, internalAverageRating = 4.5, internalCount = 3, summary = "Strong treasure-hunt energy."),
            "botanical" to ExploreReviewSummary(4.8, 204, internalAverageRating = 4.7, internalCount = 5, summary = "Easy walking paths and seasonal blooms.")
        )
        return placeIds.mapNotNull { id -> seedData[id]?.let { id to it } }.toMap()
    }
}

@Singleton
class DemoPromotionsProvider @Inject constructor() : PromotionsProvider {
    override suspend fun promotions(placeIds: List<String>): Map<String, ExplorePromotionInfo> {
        val promos = mapOf(
            "thrift" to ExplorePromotionInfo("Weekend outlet markdowns", 0.85f, "demo-catalog"),
            "market" to ExplorePromotionInfo("Spring sidewalk sale", 0.72f, "demo-catalog"),
            "corner-bistro" to ExplorePromotionInfo("Lunch combo special", 0.63f, "demo-catalog")
        )
        return placeIds.mapNotNull { promos[it]?.let { promo -> it to promo } }.toMap()
    }
}

@Singleton
class RecentDestinationVisitHistoryProvider @Inject constructor(
    private val recentDestinationRepository: RecentDestinationRepository
) : UserVisitHistoryProvider {
    override suspend fun visitedPlaceIds(): Set<String> = recentDestinationRepository.recentDestinations.first().map(PlaceResult::id).toSet()

    override suspend fun lastVisitedEpochMillis(placeId: String): Long? = if (placeId in visitedPlaceIds()) System.currentTimeMillis() - 24 * 60 * 60 * 1000 else null
}
