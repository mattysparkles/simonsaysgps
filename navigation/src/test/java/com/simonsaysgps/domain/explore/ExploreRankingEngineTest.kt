package com.simonsaysgps.domain.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreConfidenceSignal
import com.simonsaysgps.domain.model.explore.ExploreEventInfo
import com.simonsaysgps.domain.model.explore.ExploreEventTiming
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary
import com.simonsaysgps.domain.model.explore.ExploreSettings
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
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
            reviewSummary = reviewSummary(internal = 4.9, external = 4.8, count = 320)
        )
        val park = baseCandidate(
            id = "park",
            name = "Tiny Park",
            coordinate = Coordinate(40.7488, -73.9858),
            facets = setOf(ExploreFacet.PARK, ExploreFacet.OUTDOORS),
            openNow = true,
            reviewSummary = reviewSummary(internal = null, external = 4.9, count = 40)
        )

        val ranked = engine.rank(ExploreQuery(category = ExploreCategory.DELICIOUS, userLocation = origin, settings = ExploreSettings()), listOf(park, restaurant))

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

        val ranked = engine.rank(ExploreQuery(category = ExploreCategory.ON_MY_WAY, userLocation = origin, activeRoute = route, settings = ExploreSettings()), listOf(far, close))

        assertThat(ranked.first().candidate.id).isEqualTo("coffee")
        assertThat(ranked.first().offRouteDistanceMeters).isLessThan(ranked.last().offRouteDistanceMeters)
    }

    @Test
    fun `having a sale respects promotion confidence and explicit source mapping`() {
        val promo = baseCandidate(
            id = "outlet",
            name = "Outlet",
            coordinate = Coordinate(40.7489, -73.9852),
            facets = setOf(ExploreFacet.SHOPPING, ExploreFacet.SALE),
            openNow = true,
            promotions = listOf(
                ExplorePromotionInfo(
                    summary = "Weekend clearance event",
                    confidence = 0.9f,
                    source = "demo",
                    attribution = ExploreSourceAttribution("demo", "Demo", verified = true),
                    inferred = false
                )
            )
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
        assertThat(ranked.first().reasons.any { it.title.contains("Deal") }).isTrue()
    }

    @Test
    fun `events can surface even when open-now is unknown`() {
        val eventCandidate = baseCandidate(
            id = "plaza-event",
            name = "Plaza Event",
            coordinate = Coordinate(40.7487, -73.9854),
            facets = setOf(ExploreFacet.IMPORTANT, ExploreFacet.LEARNING),
            openNow = null,
            events = listOf(
                ExploreEventInfo(
                    title = "Public art fair",
                    startEpochMillis = System.currentTimeMillis() + 30_000L,
                    summary = "Happening soon",
                    timing = ExploreEventTiming.STARTING_SOON,
                    attribution = ExploreSourceAttribution("events", "Events", verified = false)
                )
            )
        )

        val ranked = engine.rank(ExploreQuery(ExploreCategory.IMPORTANT, origin), listOf(eventCandidate))

        assertThat(ranked.single().debugBreakdown["eventTiming"]).isGreaterThan(0.8)
    }

    @Test
    fun `new confidence signal boosts novelty score`() {
        val newPlace = baseCandidate(
            id = "new",
            name = "Fresh Cafe",
            coordinate = Coordinate(40.7487, -73.9854),
            facets = setOf(ExploreFacet.FOOD),
            confidenceSignals = listOf(
                ExploreConfidenceSignal(
                    label = "new",
                    confidence = 0.82f,
                    inferred = true,
                    detail = "Likely newly opened based on sparse review history.",
                    source = ExploreSourceAttribution("inference", "Inference", verified = false)
                )
            )
        )
        val familiar = baseCandidate(id = "old", name = "Classic Diner", coordinate = Coordinate(40.7488, -73.9855), facets = setOf(ExploreFacet.FOOD), visitedBefore = true)

        val ranked = engine.rank(ExploreQuery(ExploreCategory.NEW, origin), listOf(familiar, newPlace))

        assertThat(ranked.first().candidate.id).isEqualTo("new")
        assertThat(ranked.first().debugBreakdown["novelty"]).isGreaterThan(ranked.last().debugBreakdown["novelty"])
    }

    private fun reviewSummary(internal: Double?, external: Double, count: Int): ExploreReviewSummary {
        val sources = buildList {
            internal?.let {
                add(ExploreReviewSourceSummary("internal", "Simon Says GPS", it, 12, internal = true, attribution = ExploreSourceAttribution("internal", "Simon Says GPS", verified = true)))
            }
            add(ExploreReviewSourceSummary("external", "Provider", external, count, internal = false, attribution = ExploreSourceAttribution("external", "Provider", verified = false)))
        }
        return ExploreReviewSummary(
            averageRating = internal ?: external,
            totalCount = count + if (internal != null) 12 else 0,
            internalAverageRating = internal,
            internalCount = if (internal != null) 12 else 0,
            summary = "Review summary",
            sources = sources
        )
    }

    private fun baseCandidate(
        id: String,
        name: String,
        coordinate: Coordinate,
        facets: Set<ExploreFacet>,
        openNow: Boolean? = true,
        reviewSummary: ExploreReviewSummary? = null,
        promotions: List<ExplorePromotionInfo> = emptyList(),
        events: List<ExploreEventInfo> = emptyList(),
        confidenceSignals: List<ExploreConfidenceSignal> = emptyList(),
        visitedBefore: Boolean = false
    ) = ExploreCandidate(
        id = id,
        name = name,
        typeLabel = name,
        address = "123 Test St",
        coordinate = coordinate,
        facets = facets,
        openNow = openNow,
        reviewSummary = reviewSummary,
        promotionSignals = promotions,
        eventSignals = events,
        accessible = true,
        kidFriendly = true,
        quietScore = 0.7f,
        sourceConfidence = 0.8f,
        confidenceSignals = confidenceSignals,
        visitedBefore = visitedBefore
    )
}
