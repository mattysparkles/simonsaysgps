package com.simonsaysgps.data.repository.explore

import android.util.Log
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreConfidenceSignal
import com.simonsaysgps.domain.model.explore.ExploreCanonicalPlaceIdFactory
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreRepositorySnapshot
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.repository.explore.EventProvider
import com.simonsaysgps.domain.repository.explore.ExploreRepository
import com.simonsaysgps.domain.repository.explore.PlaceDetailsProvider
import com.simonsaysgps.domain.repository.explore.PlaceDiscoveryProvider
import com.simonsaysgps.domain.repository.explore.PromotionSignalProvider
import com.simonsaysgps.domain.repository.explore.ReviewProvider
import com.simonsaysgps.domain.repository.explore.UserVisitHistoryProvider
import com.simonsaysgps.domain.util.GeoUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DefaultExploreRepository @Inject constructor(
    private val placeDiscoveryProviders: Set<@JvmSuppressWildcards PlaceDiscoveryProvider>,
    private val placeDetailsProviders: Set<@JvmSuppressWildcards PlaceDetailsProvider>,
    private val reviewProviders: Set<@JvmSuppressWildcards ReviewProvider>,
    private val eventProviders: Set<@JvmSuppressWildcards EventProvider>,
    private val promotionSignalProviders: Set<@JvmSuppressWildcards PromotionSignalProvider>,
    private val userVisitHistoryProvider: UserVisitHistoryProvider
) : ExploreRepository {
    private data class CacheEntry(val snapshot: ExploreRepositorySnapshot, val timestampMillis: Long)
    private val cache = linkedMapOf<String, CacheEntry>()

    override suspend fun fetch(query: ExploreQuery): ExploreRepositorySnapshot {
        val cacheKey = listOf(query.category.name, query.userLocation.latitude, query.userLocation.longitude, query.settings.defaultRadiusMiles).joinToString(":" )
        cache[cacheKey]?.takeIf { query.nowEpochMillis - it.timestampMillis < CACHE_TTL_MS }?.let {
            return it.snapshot.copy(
                providerStatuses = it.snapshot.providerStatuses + ExploreProviderStatus("explore-cache", true, "Recent Explore aggregation cache hit.")
            )
        }

        val statuses = mutableListOf<ExploreProviderStatus>()
        val discovered = mutableListOf<ExploreCandidate>()
        for (provider in placeDiscoveryProviders.sortedBy { it.providerId }) {
            runCatching { provider.discoverPlaces(query) }
                .onSuccess {
                    discovered += it
                    statuses += ExploreProviderStatus(provider.providerId, true, if (it.isEmpty()) "No place matches returned." else "${it.size} place candidates discovered.")
                }
                .onFailure {
                    Log.w(TAG, "Place discovery provider ${provider.providerId} failed", it)
                    statuses += ExploreProviderStatus(provider.providerId, false, "Place discovery unavailable: ${it.message ?: "unknown error"}.")
                }
        }

        var merged = mergeDuplicates(discovered)

        for (provider in placeDetailsProviders.sortedBy { it.providerId }) {
            runCatching { provider.enrichPlaces(query, merged) }
                .onSuccess { enriched ->
                    merged = merged.map { enriched[it.id] ?: it }
                    statuses += ExploreProviderStatus(provider.providerId, true, if (enriched.isEmpty()) "No additional detail matches." else "Merged ${enriched.size} detail enrichments.")
                }
                .onFailure {
                    Log.w(TAG, "Place details provider ${provider.providerId} failed", it)
                    statuses += ExploreProviderStatus(provider.providerId, false, "Place details unavailable: ${it.message ?: "unknown error"}.")
                }
        }

        val reviewMap = mutableMapOf<String, MutableList<com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary>>()
        for (provider in reviewProviders.sortedBy { it.providerId }) {
            runCatching { provider.reviews(query, merged) }
                .onSuccess { reviews ->
                    reviews.forEach { (id, values) -> reviewMap.getOrPut(id) { mutableListOf() }.addAll(values) }
                    statuses += ExploreProviderStatus(provider.providerId, true, if (reviews.isEmpty()) "No review signals returned." else "Review summaries added for ${reviews.size} places.")
                }
                .onFailure {
                    Log.w(TAG, "Review provider ${provider.providerId} failed", it)
                    statuses += ExploreProviderStatus(provider.providerId, false, "Review provider unavailable: ${it.message ?: "unknown error"}.")
                }
        }

        val eventMap = mutableMapOf<String, MutableList<com.simonsaysgps.domain.model.explore.ExploreEventInfo>>()
        for (provider in eventProviders.sortedBy { it.providerId }) {
            runCatching { provider.events(query, merged) }
                .onSuccess { events ->
                    events.forEach { (id, values) -> eventMap.getOrPut(id) { mutableListOf() }.addAll(values) }
                    statuses += ExploreProviderStatus(provider.providerId, true, if (events.isEmpty()) "No event signals returned." else "Event signals added for ${events.size} places.")
                }
                .onFailure {
                    Log.w(TAG, "Event provider ${provider.providerId} failed", it)
                    statuses += ExploreProviderStatus(provider.providerId, false, "Event provider unavailable: ${it.message ?: "unknown error"}.")
                }
        }

        val promoMap = mutableMapOf<String, MutableList<com.simonsaysgps.domain.model.explore.ExplorePromotionInfo>>()
        for (provider in promotionSignalProviders.sortedBy { it.providerId }) {
            runCatching { provider.promotions(query, merged) }
                .onSuccess { promotions ->
                    promotions.forEach { (id, values) -> promoMap.getOrPut(id) { mutableListOf() }.addAll(values) }
                    statuses += ExploreProviderStatus(provider.providerId, true, if (promotions.isEmpty()) "No promotion signals returned." else "Promotion signals added for ${promotions.size} places.")
                }
                .onFailure {
                    Log.w(TAG, "Promotion provider ${provider.providerId} failed", it)
                    statuses += ExploreProviderStatus(provider.providerId, false, "Promotion provider unavailable: ${it.message ?: "unknown error"}.")
                }
        }

        merged = merged.map { candidate ->
            val orderedSources = reviewMap[candidate.id].orEmpty().sortedByDescending { it.internal }
            val internal = orderedSources.filter { it.internal }
            val thirdParty = orderedSources.filterNot { it.internal }
            val includedSources = if (query.settings.includeThirdPartyReviewSummariesWhenAvailable) internal + thirdParty else internal
            val reviewSummary = includedSources.takeIf { it.isNotEmpty() }?.let { sources ->
                val averages = sources.mapNotNull { it.averageRating }
                ExploreReviewSummary(
                    averageRating = internal.firstOrNull()?.averageRating ?: averages.average().takeIf { !it.isNaN() } ?: 0.0,
                    totalCount = sources.sumOf { it.reviewCount },
                    internalAverageRating = internal.firstOrNull()?.averageRating,
                    internalCount = internal.sumOf { it.reviewCount },
                    summary = sources.firstNotNullOfOrNull { it.summary },
                    sources = sources
                )
            }
            val confidenceSignals = candidate.confidenceSignals + promoMap[candidate.id].orEmpty().map {
                ExploreConfidenceSignal(
                    label = "sale",
                    confidence = it.confidence,
                    inferred = it.inferred,
                    detail = it.summary,
                    source = it.attribution
                )
            }
            candidate.copy(
                reviewSummary = reviewSummary,
                eventSignals = eventMap[candidate.id].orEmpty().sortedBy { it.startEpochMillis },
                promotionSignals = promoMap[candidate.id].orEmpty().sortedByDescending { it.confidence },
                confidenceSignals = confidenceSignals
            )
        }

        runCatching { userVisitHistoryProvider.enrichPlaces(merged) }
            .onSuccess { visited ->
                merged = merged.map { visited[it.id] ?: it }
                statuses += ExploreProviderStatus("visit-history", true, if (visited.isEmpty()) "No recent saved/visited matches." else "Internal visit history enriched ${visited.size} places.")
            }
            .onFailure {
                Log.w(TAG, "Visit history provider failed", it)
                statuses += ExploreProviderStatus("visit-history", false, "Visit history unavailable: ${it.message ?: "unknown error"}.")
            }

        val snapshot = ExploreRepositorySnapshot(candidates = merged, providerStatuses = statuses)
        cache[cacheKey] = CacheEntry(snapshot, query.nowEpochMillis)
        if (cache.size > 8) cache.remove(cache.entries.first().key)
        return snapshot
    }

    private fun mergeDuplicates(candidates: List<ExploreCandidate>): List<ExploreCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<ExploreCandidate>>()
        candidates.forEach { candidate ->
            val existing = groups.firstOrNull { group -> group.any { isDuplicate(it, candidate) } }
            if (existing != null) existing += candidate else groups += mutableListOf(candidate)
        }
        return groups.map { group ->
            val sorted = group.sortedByDescending { it.sourceConfidence }
            val primary = sorted.first()
            val allFacets = group.flatMap { it.facets }.toSet()
            val allSources = group.flatMap { it.sourceAttributions }.distinctBy { it.provider + it.label }
            val allLinks = group.flatMap { it.providerLinks }.distinctBy { it.provider + it.externalId }
            val allHints = group.flatMap { it.whyChosenHints }.distinct()
            val allConfidenceSignals = group.flatMap { it.confidenceSignals }.distinctBy { it.label + it.detail + it.source.provider }
            primary.copy(
                id = ExploreCanonicalPlaceIdFactory.fromCandidate(primary),
                typeLabel = group.maxByOrNull { it.typeLabel.length }?.typeLabel ?: primary.typeLabel,
                address = group.maxByOrNull { it.address.length }?.address ?: primary.address,
                facets = allFacets,
                phoneNumber = group.firstNotNullOfOrNull { it.phoneNumber },
                websiteUrl = group.firstNotNullOfOrNull { it.websiteUrl },
                sourceConfidence = group.maxOf { it.sourceConfidence }.coerceIn(0.0f, 1.0f),
                accessible = group.firstNotNullOfOrNull { it.accessible },
                kidFriendly = group.firstNotNullOfOrNull { it.kidFriendly },
                quietScore = group.mapNotNull { it.quietScore }.maxOrNull(),
                alcoholFocused = group.any { it.alcoholFocused },
                adultOriented = group.any { it.adultOriented },
                recentlyOpenedOrTrending = group.any { it.recentlyOpenedOrTrending },
                sourceAttributions = allSources,
                providerLinks = allLinks,
                confidenceSignals = allConfidenceSignals,
                whyChosenHints = allHints
            )
        }
    }


    private fun isDuplicate(left: ExploreCandidate, right: ExploreCandidate): Boolean {
        val explicitLink = left.providerLinks.any { leftLink -> right.providerLinks.any { it.provider == leftLink.provider && it.externalId == leftLink.externalId } }
        if (explicitLink) return true
        val samePhone = left.phoneNumber != null && right.phoneNumber != null && digitsOnly(left.phoneNumber) == digitsOnly(right.phoneNumber)
        val nameClose = normalize(left.name) == normalize(right.name)
        val addressClose = normalize(left.address).contains(normalize(right.address)) || normalize(right.address).contains(normalize(left.address))
        val distanceMeters = GeoUtils.distanceMeters(left.coordinate, right.coordinate)
        return samePhone || (nameClose && distanceMeters <= 120.0) || (nameClose && addressClose && distanceMeters <= 250.0)
    }

    private fun digitsOnly(value: String): String = value.filter(Char::isDigit)
    private fun normalize(value: String): String = value.lowercase().replace("[^a-z0-9]".toRegex(), "")

    private companion object {
        const val TAG = "DefaultExploreRepo"
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
