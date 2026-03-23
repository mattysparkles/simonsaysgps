package com.simonsaysgps.data.repository.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExploreReason
import com.simonsaysgps.domain.model.explore.ExploreResult
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.simonsaysgps.domain.repository.explore.InternalReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultPlaceDetailRepositoryTest {
    @Test
    fun `mapping keeps internal and external review data separate`() = runTest {
        val repository = DefaultPlaceDetailRepository(FakeInternalReviewRepository())
        val detail = repository.observePlaceDetail(seedResult()).firstValue()

        assertThat(detail.internalAggregate?.count).isEqualTo(1)
        assertThat(detail.externalReviewSummaries).hasSize(1)
        assertThat(detail.externalReviewSummaries.single().providerLabel).isEqualTo("Provider summary")
        assertThat(detail.whyChosen).contains("easy detour")
    }

    private fun seedResult() = ExploreResult(
        candidate = ExploreCandidate(
            id = "place-1",
            name = "Cafe",
            typeLabel = "Cafe",
            address = "1 Main Street",
            coordinate = Coordinate(40.0, -73.0),
            facets = setOf(ExploreFacet.FOOD),
            openNow = true,
            reviewSummary = ExploreReviewSummary(
                averageRating = 4.4,
                totalCount = 12,
                sources = listOf(
                    ExploreReviewSourceSummary(
                        provider = "provider",
                        providerLabel = "Provider summary",
                        averageRating = 4.4,
                        reviewCount = 12,
                        summary = "Provider says this place is popular.",
                        internal = false,
                        attribution = ExploreSourceAttribution("provider", "Provider summary", verified = false)
                    )
                )
            )
        ),
        score = 0.9,
        confidence = 0.8f,
        distanceMeters = 1609.34,
        estimatedDetourMinutes = 4,
        reasons = listOf(ExploreReason("why", "An easy detour with reliable stops.", 1.0, 0.8f)),
        debugBreakdown = emptyMap()
    )

    private class FakeInternalReviewRepository : InternalReviewRepository {
        override val localAuthorDisplayName: Flow<String> = flowOf("Local driver")
        private val reviews = MutableStateFlow(
            listOf(
                InternalPlaceReview(
                    internalReviewId = "review-1",
                    canonicalPlaceId = "place-1",
                    authorDisplayName = "Local driver",
                    rating = 5,
                    reviewText = "Quiet and delicious place.",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                    tags = setOf(PlaceReviewTag.QUIET, PlaceReviewTag.DELICIOUS)
                )
            )
        )

        override fun observeReviews(canonicalPlaceId: String): Flow<List<InternalPlaceReview>> = reviews
        override fun observeOwnReview(canonicalPlaceId: String): Flow<InternalPlaceReview?> = flowOf(reviews.value.first())
        override suspend fun upsert(review: InternalPlaceReview) { reviews.value = listOf(review) }
        override suspend fun remove(reviewId: String) { reviews.value = emptyList() }
    }
}

private suspend fun <T> Flow<T>.firstValue(): T = first()
