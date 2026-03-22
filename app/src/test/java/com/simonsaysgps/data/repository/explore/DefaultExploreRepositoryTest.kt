package com.simonsaysgps.data.repository.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreConfidenceSignal
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreProviderLink
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.repository.explore.EventProvider
import com.simonsaysgps.domain.repository.explore.PlaceDetailsProvider
import com.simonsaysgps.domain.repository.explore.PlaceDiscoveryProvider
import com.simonsaysgps.domain.repository.explore.PromotionSignalProvider
import com.simonsaysgps.domain.repository.explore.ReviewProvider
import com.simonsaysgps.domain.repository.explore.UserVisitHistoryProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultExploreRepositoryTest {
    private val query = ExploreQuery(category = com.simonsaysgps.domain.model.explore.ExploreCategory.DELICIOUS, userLocation = Coordinate(40.0, -73.0))

    @Test
    fun `duplicate place merging preserves source attribution and provider links`() = runTest {
        val left = place(id = "provider-a-1", name = "Corner Cafe", address = "10 Main Street", coordinate = Coordinate(40.0, -73.0), provider = "provider-a", externalId = "1")
        val right = place(id = "provider-b-2", name = "Corner Cafe", address = "10 Main St", coordinate = Coordinate(40.0005, -73.0004), provider = "provider-b", externalId = "2")
        val repository = repository(
            placeDiscoveryProviders = setOf(FakePlaceDiscoveryProvider("provider-a", listOf(left)), FakePlaceDiscoveryProvider("provider-b", listOf(right)))
        )

        val snapshot = repository.fetch(query)

        assertThat(snapshot.candidates).hasSize(1)
        assertThat(snapshot.candidates.single().sourceAttributions.map { it.provider }).containsExactly("provider-a", "provider-b")
        assertThat(snapshot.candidates.single().providerLinks).hasSize(2)
    }

    @Test
    fun `review ordering keeps internal reviews first and grouped by source`() = runTest {
        val place = place()
        val repository = repository(
            reviewProviders = setOf(object : ReviewProvider {
                override val providerId: String = "reviews"
                override suspend fun reviews(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExploreReviewSourceSummary>> {
                    val id = candidates.single().id
                    return mapOf(
                        id to listOf(
                            ExploreReviewSourceSummary("third-party", "Third Party", 4.3, 90, internal = false, attribution = ExploreSourceAttribution("third-party", "Third Party", verified = false)),
                            ExploreReviewSourceSummary("internal", "Simon Says GPS", 4.8, 6, internal = true, attribution = ExploreSourceAttribution("internal", "Simon Says GPS", verified = true))
                        )
                    )
                }
            })
        )

        val summary = repository.fetch(query).candidates.single().reviewSummary!!

        assertThat(summary.sources.first().internal).isTrue()
        assertThat(summary.thirdPartySources.single().provider).isEqualTo("third-party")
    }

    @Test
    fun `partial provider failure still returns surviving results and failure status`() = runTest {
        val repository = repository(
            placeDiscoveryProviders = setOf(
                FakePlaceDiscoveryProvider("ok", listOf(place())),
                object : PlaceDiscoveryProvider {
                    override val providerId: String = "boom"
                    override suspend fun discoverPlaces(query: ExploreQuery): List<ExploreCandidate> = error("network down")
                }
            )
        )

        val snapshot = repository.fetch(query)

        assertThat(snapshot.candidates).hasSize(1)
        assertThat(snapshot.providerStatuses.any { it.provider == "boom" && !it.available }).isTrue()
    }

    @Test
    fun `attribution and confidence mapping attaches sale signals`() = runTest {
        val repository = repository(
            promotionSignalProviders = setOf(object : PromotionSignalProvider {
                override val providerId: String = "promo"
                override suspend fun promotions(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExplorePromotionInfo>> =
                    mapOf(candidates.single().id to listOf(ExplorePromotionInfo("Possible special", 0.62f, "promo", ExploreSourceAttribution("promo", "Promo", verified = false), inferred = true)))
            })
        )

        val candidate = repository.fetch(query).candidates.single()

        assertThat(candidate.promotionSignals.single().inferred).isTrue()
        assertThat(candidate.confidenceSignals.single { it.label == "sale" }.confidence).isWithin(0.001f).of(0.62f)
    }

    private fun repository(
        placeDiscoveryProviders: Set<PlaceDiscoveryProvider> = setOf(FakePlaceDiscoveryProvider("places", listOf(place()))),
        placeDetailsProviders: Set<PlaceDetailsProvider> = emptySet(),
        reviewProviders: Set<ReviewProvider> = emptySet(),
        eventProviders: Set<EventProvider> = emptySet(),
        promotionSignalProviders: Set<PromotionSignalProvider> = emptySet(),
        userVisitHistoryProvider: UserVisitHistoryProvider = object : UserVisitHistoryProvider {
            override suspend fun enrichPlaces(candidates: List<ExploreCandidate>): Map<String, ExploreCandidate> = emptyMap()
        }
    ) = DefaultExploreRepository(
        placeDiscoveryProviders = placeDiscoveryProviders,
        placeDetailsProviders = placeDetailsProviders,
        reviewProviders = reviewProviders,
        eventProviders = eventProviders,
        promotionSignalProviders = promotionSignalProviders,
        userVisitHistoryProvider = userVisitHistoryProvider
    )

    private fun place(
        id: String = "place-1",
        name: String = "Cafe",
        address: String = "123 Main Street",
        coordinate: Coordinate = Coordinate(40.0, -73.0),
        provider: String = "places",
        externalId: String = "place-1"
    ) = ExploreCandidate(
        id = id,
        name = name,
        typeLabel = "Cafe",
        address = address,
        coordinate = coordinate,
        facets = setOf(ExploreFacet.FOOD),
        openNow = true,
        sourceConfidence = 0.8f,
        sourceAttributions = listOf(ExploreSourceAttribution(provider, provider, verified = true)),
        providerLinks = listOf(ExploreProviderLink(provider, externalId))
    )

    private class FakePlaceDiscoveryProvider(
        override val providerId: String,
        private val candidates: List<ExploreCandidate>
    ) : PlaceDiscoveryProvider {
        override suspend fun discoverPlaces(query: ExploreQuery): List<ExploreCandidate> = candidates
    }
}
