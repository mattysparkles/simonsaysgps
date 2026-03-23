package com.simonsaysgps.data.explore

import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.model.explore.InternalReviewAggregateCalculator
import com.simonsaysgps.domain.repository.explore.InternalReviewRepository
import com.simonsaysgps.domain.repository.explore.ReviewProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class InternalReviewExploreProvider @Inject constructor(
    private val internalReviewRepository: InternalReviewRepository
) : ReviewProvider {
    override val providerId: String = "internal-community"

    override suspend fun reviews(
        query: ExploreQuery,
        candidates: List<com.simonsaysgps.domain.model.explore.ExploreCandidate>
    ): Map<String, List<ExploreReviewSourceSummary>> {
        return candidates.mapNotNull { candidate ->
            val aggregate = InternalReviewAggregateCalculator.calculate(internalReviewRepository.observeReviews(candidate.id).first())
                ?: return@mapNotNull null
            candidate.id to listOf(
                ExploreReviewSourceSummary(
                    provider = providerId,
                    providerLabel = "Simon Says GPS",
                    averageRating = aggregate.averageRating,
                    reviewCount = aggregate.count,
                    summary = aggregate.topTags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Tagged: ") { it.label },
                    internal = true,
                    attribution = ExploreSourceAttribution(
                        provider = providerId,
                        label = "Simon Says GPS",
                        verified = true,
                        debugDetail = "Local device-scoped internal review aggregate"
                    ),
                    confidence = 0.99f
                )
            )
        }.toMap()
    }
}
