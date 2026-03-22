package com.simonsaysgps.data.repository.explore

import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreRepositorySnapshot
import com.simonsaysgps.domain.repository.explore.EventDataProvider
import com.simonsaysgps.domain.repository.explore.ExploreRepository
import com.simonsaysgps.domain.repository.explore.PlaceDataProvider
import com.simonsaysgps.domain.repository.explore.PromotionsProvider
import com.simonsaysgps.domain.repository.explore.ReviewsProvider
import com.simonsaysgps.domain.repository.explore.UserVisitHistoryProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultExploreRepository @Inject constructor(
    private val placeDataProvider: PlaceDataProvider,
    private val eventDataProvider: EventDataProvider,
    private val userVisitHistoryProvider: UserVisitHistoryProvider,
    private val reviewsProvider: ReviewsProvider,
    private val promotionsProvider: PromotionsProvider
) : ExploreRepository {
    override suspend fun fetch(query: ExploreQuery): ExploreRepositorySnapshot {
        val statuses = mutableListOf<ExploreProviderStatus>()

        val places = runCatching {
            placeDataProvider.discoverPlaces(query).also {
                statuses += ExploreProviderStatus("place-data", true, "Nearby demo/catalog discovery is active.")
            }
        }.getOrElse {
            statuses += ExploreProviderStatus("place-data", false, "Place discovery unavailable: ${it.message ?: "unknown error"}.")
            emptyList()
        }

        val events = runCatching {
            eventDataProvider.discoverEvents(query).also {
                statuses += ExploreProviderStatus("event-data", true, if (it.isEmpty()) "No current event matches for this query." else "Event timing data merged when available.")
            }
        }.getOrElse {
            statuses += ExploreProviderStatus("event-data", false, "Event provider unavailable: ${it.message ?: "unknown error"}.")
            emptyMap()
        }

        val visitedPlaceIds = runCatching {
            userVisitHistoryProvider.visitedPlaceIds().also {
                statuses += ExploreProviderStatus("visit-history", true, if (it.isEmpty()) "No known visit history yet." else "Recent destination history is being used for novelty.")
            }
        }.getOrElse {
            statuses += ExploreProviderStatus("visit-history", false, "Visit history unavailable: ${it.message ?: "unknown error"}.")
            emptySet()
        }

        val placeIds = places.map(ExploreCandidate::id)
        val reviews = runCatching {
            reviewsProvider.reviewSummaries(placeIds).also {
                statuses += ExploreProviderStatus("reviews", true, if (it.isEmpty()) "Review signals are limited for this batch." else "Internal/demo reviews merged into ranking.")
            }
        }.getOrElse {
            statuses += ExploreProviderStatus("reviews", false, "Review provider unavailable: ${it.message ?: "unknown error"}.")
            emptyMap()
        }

        val promotions = runCatching {
            promotionsProvider.promotions(placeIds).also {
                statuses += ExploreProviderStatus("promotions", true, if (it.isEmpty()) "No sale signals available for this batch." else "Promotion/deal signals merged when present.")
            }
        }.getOrElse {
            statuses += ExploreProviderStatus("promotions", false, "Promotions provider unavailable: ${it.message ?: "unknown error"}.")
            emptyMap()
        }

        val merged = places.map { place ->
            place.copy(
                eventInfo = events[place.id],
                reviewSummary = reviews[place.id],
                promotionInfo = promotions[place.id],
                visitedBefore = place.id in visitedPlaceIds,
                lastVisitedEpochMillis = if (place.id in visitedPlaceIds) userVisitHistoryProvider.lastVisitedEpochMillis(place.id) else null
            )
        }

        return ExploreRepositorySnapshot(candidates = merged, providerStatuses = statuses)
    }
}
