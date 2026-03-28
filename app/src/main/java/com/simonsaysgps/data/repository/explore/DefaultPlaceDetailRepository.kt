package com.simonsaysgps.data.repository.explore

import com.simonsaysgps.domain.model.explore.ExternalReviewSummaryBlock
import com.simonsaysgps.domain.model.explore.ExploreResult
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.InternalReviewAggregateCalculator
import com.simonsaysgps.domain.model.explore.PlaceDetailRecord
import com.simonsaysgps.domain.repository.explore.InternalReviewRepository
import com.simonsaysgps.domain.repository.explore.PlaceDetailRepository
import com.simonsaysgps.domain.repository.explore.SavedPlaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class DefaultPlaceDetailRepository @Inject constructor(
    private val internalReviewRepository: InternalReviewRepository,
    private val savedPlaceRepository: SavedPlaceRepository
) : PlaceDetailRepository {
    override fun observePlaceDetail(seed: ExploreResult): Flow<PlaceDetailRecord> {
        return internalReviewRepository.observeReviews(seed.candidate.id).combine(
            savedPlaceRepository.observeSavedPlace(seed.candidate.id)
        ) { reviews, savedPlace ->
            val internalAggregate = InternalReviewAggregateCalculator.calculate(reviews)
            PlaceDetailRecord(
                canonicalPlaceId = seed.candidate.id,
                name = seed.candidate.name,
                typeLabel = seed.candidate.typeLabel,
                address = seed.candidate.address,
                coordinate = seed.candidate.coordinate,
                openNow = seed.candidate.openNow,
                eventInfo = seed.candidate.primaryEvent,
                distanceMeters = seed.distanceMeters,
                offRouteDistanceMeters = seed.offRouteDistanceMeters,
                estimatedDetourMinutes = seed.estimatedDetourMinutes,
                whyChosen = seed.primaryWhyChosen,
                phoneNumber = seed.candidate.phoneNumber,
                websiteUrl = seed.candidate.websiteUrl,
                hoursSummary = hoursSummaryFor(seed),
                placeTags = seed.candidate.facets.map { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) }.sorted(),
                eventSnippets = seed.candidate.eventSignals.mapNotNull { it.summary ?: it.title },
                sourceAttributions = seed.candidate.sourceAttributions,
                savedPlace = savedPlace,
                internalAggregate = internalAggregate,
                internalReviews = reviews,
                externalReviewSummaries = seed.candidate.reviewSummary?.thirdPartySources.orEmpty().map(::toExternalBlock)
            )
        }
    }

    private fun toExternalBlock(summary: ExploreReviewSourceSummary) = ExternalReviewSummaryBlock(
        provider = summary.provider,
        providerLabel = summary.providerLabel,
        averageRating = summary.averageRating,
        reviewCount = summary.reviewCount,
        summary = summary.summary,
        attribution = summary.attribution,
        confidence = summary.confidence,
        excerpts = listOfNotNull(summary.summary)
    )

    private fun hoursSummaryFor(seed: ExploreResult): String? {
        val primaryEvent = seed.candidate.primaryEvent
        return when {
            primaryEvent != null -> primaryEvent.summary ?: primaryEvent.title
            seed.candidate.openNow == true -> "Open now"
            seed.candidate.openNow == false -> "Closed right now"
            seed.candidate.confidenceSignals.any { it.label == "hours-known" } -> "Provider has hours metadata, but the current status is unknown."
            else -> null
        }
    }
}
