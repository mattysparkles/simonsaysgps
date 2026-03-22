package com.simonsaysgps.domain.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreEventInfo
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary
import com.simonsaysgps.domain.model.explore.ExploreSettings
import com.simonsaysgps.domain.service.explore.ExploreRankingEngine
import org.junit.Test

class ExploreRankingEngineTest {
    private val engine = ExploreRankingEngine()
    private val origin = Coordinate(40.7484, -73.9857)

    @Test
    fun `delicious strongly favors nearby open restaurants with stronger reviews`() {
        val restaurant = baseCandidate(
            id = "pizza",
            name = "Pizza Place",
            coordinate = Coordinate(40.7490, -73.9850),
            facets = setOf(ExploreFacet.FOOD),
            openNow = true,
            reviewSummary = ExploreReviewSummary(4.8, 320, internalAverageRating = 4.9, internalCount = 12)
        )
        val park = baseCandidate(
            id = "park",
            name = "Tiny Park",
            coordinate = Coordinate(40.7488, -73.9858),
            facets = setOf(ExploreFacet.PARK, ExploreFacet.OUTDOORS),
            openNow = true,
            reviewSummary = ExploreReviewSummary(4.9, 40)
        )

        val ranked = engine.rank(
            ExploreQuery(
                category = ExploreCategory.DELICIOUS,
                userLocation = origin,
                settings = ExploreSettings()
            ),
            listOf(park, restaurant)
        )

        assertThat(ranked.first().candidate.id).isEqualTo("pizza")
        assertThat(ranked.first().primaryWhyChosen).contains("delicious")
    }

    @Test
    fun `on my way boosts candidates with lower route detour distance`() {
        val route = Route(
            geometry = listOf(origin, Coordinate(40.7510, -73.9820)),
            maneuvers = emptyList(),
            totalDistanceMeters = 1000.0,
            totalDurationSeconds = 300.0,
            etaEpochSeconds = 0L
        )
        val close = baseCandidate("coffee", "Coffee", Coordinate(40.7493, -73.9846), setOf(ExploreFacet.FOOD, ExploreFacet.DRINK), openNow = true)
        val far = baseCandidate("museum", "Museum", Coordinate(40.7600, -73.9700), setOf(ExploreFacet.IMPORTANT, ExploreFacet.LEARNING), openNow = true)

        val ranked = engine.rank(
            ExploreQuery(
                category = ExploreCategory.ON_MY_WAY,
                userLocation = origin,
                activeRoute = route,
                settings = ExploreSettings()
            ),
            listOf(far, close)
        )

        assertThat(ranked.first().candidate.id).isEqualTo("coffee")
        assertThat(ranked.first().offRouteDistanceMeters).isLessThan(ranked.last().offRouteDistanceMeters)
    }

    @Test
    fun `having a sale respects promotion signals when data exists`() {
        val promo = baseCandidate(
            id = "outlet",
            name = "Outlet",
            coordinate = Coordinate(40.7489, -73.9852),
            facets = setOf(ExploreFacet.SHOPPING, ExploreFacet.SALE),
            openNow = true,
            promotionInfo = ExplorePromotionInfo("Weekend clearance event", 0.9f, "demo")
        )
        val noPromo = baseCandidate(
            id = "bookstore",
            name = "Bookstore",
            coordinate = Coordinate(40.7488, -73.9854),
            facets = setOf(ExploreFacet.SHOPPING, ExploreFacet.LEARNING),
            openNow = true
        )

        val ranked = engine.rank(ExploreQuery(ExploreCategory.HAVING_A_SALE, origin), listOf(noPromo, promo))

        assertThat(ranked.first().candidate.id).isEqualTo("outlet")
    }

    @Test
    fun `events can surface even when open-now is unknown`() {
        val eventCandidate = baseCandidate(
            id = "plaza-event",
            name = "Plaza Event",
            coordinate = Coordinate(40.7487, -73.9854),
            facets = setOf(ExploreFacet.IMPORTANT, ExploreFacet.LEARNING),
            openNow = null,
            eventInfo = ExploreEventInfo("Public art fair", System.currentTimeMillis() + 30_000L)
        )

        val ranked = engine.rank(ExploreQuery(ExploreCategory.IMPORTANT, origin), listOf(eventCandidate))

        assertThat(ranked.single().debugBreakdown["eventTiming"]).isGreaterThan(0.8)
    }

    private fun baseCandidate(
        id: String,
        name: String,
        coordinate: Coordinate,
        facets: Set<ExploreFacet>,
        openNow: Boolean? = true,
        reviewSummary: ExploreReviewSummary? = null,
        promotionInfo: ExplorePromotionInfo? = null,
        eventInfo: ExploreEventInfo? = null
    ) = ExploreCandidate(
        id = id,
        name = name,
        typeLabel = name,
        address = "123 Test St",
        coordinate = coordinate,
        facets = facets,
        openNow = openNow,
        reviewSummary = reviewSummary,
        promotionInfo = promotionInfo,
        eventInfo = eventInfo,
        accessible = true,
        kidFriendly = true,
        quietScore = 0.7f,
        sourceConfidence = 0.8f
    )
}
