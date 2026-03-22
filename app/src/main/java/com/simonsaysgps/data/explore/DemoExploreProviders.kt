package com.simonsaysgps.data.explore

import android.util.Log
import com.simonsaysgps.data.remote.NominatimApi
import com.simonsaysgps.data.remote.NominatimPlaceDto
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreConfidenceSignal
import com.simonsaysgps.domain.model.explore.ExploreEventInfo
import com.simonsaysgps.domain.model.explore.ExploreEventTiming
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExplorePromotionInfo
import com.simonsaysgps.domain.model.explore.ExploreProviderLink
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.repository.RecentDestinationRepository
import com.simonsaysgps.domain.repository.explore.EventProvider
import com.simonsaysgps.domain.repository.explore.PlaceDetailsProvider
import com.simonsaysgps.domain.repository.explore.PlaceDiscoveryProvider
import com.simonsaysgps.domain.repository.explore.PromotionSignalProvider
import com.simonsaysgps.domain.repository.explore.ReviewProvider
import com.simonsaysgps.domain.repository.explore.UserVisitHistoryProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ExploreProviders"

@Singleton
class NominatimPlaceDiscoveryProvider @Inject constructor(
    private val api: NominatimApi
) : PlaceDiscoveryProvider {
    override val providerId: String = "nominatim-place-discovery"

    private val cacheMutex = Mutex()
    private val dtoCache = linkedMapOf<String, NominatimPlaceDto>()

    override suspend fun discoverPlaces(query: ExploreQuery): List<ExploreCandidate> {
        val origin = query.userLocation
        val viewbox = buildViewbox(origin, query.settings.defaultRadiusMiles.toDouble())
        val terms = query.category.searchTerms()
        val results = terms.flatMap { term ->
            runCatching {
                api.search(
                    query = term,
                    limit = 6,
                    viewbox = viewbox,
                    bounded = 1
                )
            }.onFailure {
                Log.w(TAG, "Nominatim search failed for term '$term': ${it.message}")
            }.getOrDefault(emptyList())
        }
        val unique = results.distinctBy { it.place_id }
        cacheMutex.withLock {
            unique.forEach { dtoCache[it.place_id.toString()] = it }
        }
        return unique.map { it.toCandidate() }
    }

    suspend fun cachedPlace(externalId: String): NominatimPlaceDto? = cacheMutex.withLock { dtoCache[externalId] }

    private fun buildViewbox(origin: Coordinate, radiusMiles: Double): String {
        val latDelta = radiusMiles / 69.0
        val lonDelta = radiusMiles / (69.0 * kotlin.math.cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.2))
        val left = origin.longitude - lonDelta
        val right = origin.longitude + lonDelta
        val top = origin.latitude + latDelta
        val bottom = origin.latitude - latDelta
        return "$left,$top,$right,$bottom"
    }

    private fun ExploreCategory.searchTerms(): List<String> = when (this) {
        ExploreCategory.DELICIOUS -> listOf("restaurant", "cafe", "bakery")
        ExploreCategory.OPEN_NOW -> listOf("cafe", "pharmacy", "restaurant")
        ExploreCategory.ON_MY_WAY -> listOf("coffee", "fuel", "grocery")
        ExploreCategory.CLOSE_TO_HOME -> listOf("park", "library", "supermarket")
        ExploreCategory.GOOD_FOR_KIDS -> listOf("playground", "museum", "park")
        ExploreCategory.QUIET -> listOf("library", "garden", "park")
        ExploreCategory.IMPORTANT -> listOf("hospital", "pharmacy", "town hall")
        ExploreCategory.FUN -> listOf("museum", "cinema", "arcade")
        ExploreCategory.NEW -> listOf("cafe", "restaurant", "market")
        ExploreCategory.NEVER_BEEN -> listOf("park", "museum", "restaurant")
        ExploreCategory.SPECIAL -> listOf("landmark", "garden", "museum")
        ExploreCategory.I_CAN_SHOP -> listOf("mall", "market", "shop")
        ExploreCategory.I_CAN_LEARN -> listOf("museum", "library", "visitor center")
        ExploreCategory.HAVING_A_SALE -> listOf("mall", "market", "supermarket")
    }

    private fun NominatimPlaceDto.toCandidate(): ExploreCandidate {
        val placeName = name ?: namedetails?.get("name") ?: display_name.substringBefore(',')
        val facets = inferFacets(category = category, type = type, displayName = display_name, tags = extratags)
        val addressText = address?.values?.joinToString() ?: display_name
        val attribution = ExploreSourceAttribution(
            provider = providerId,
            label = "OpenStreetMap / Nominatim",
            verified = true,
            debugDetail = "osm_type=${osm_type.orEmpty()} osm_id=${osm_id ?: place_id}"
        )
        return ExploreCandidate(
            id = "nominatim-$place_id",
            name = placeName,
            typeLabel = type?.replaceFirstChar { it.uppercase() } ?: category?.replaceFirstChar { it.uppercase() } ?: "Place",
            address = addressText,
            coordinate = Coordinate(lat.toDouble(), lon.toDouble()),
            facets = facets,
            openNow = extratags?.get("opening_hours") != null,
            phoneNumber = extratags?.get("phone") ?: extratags?.get("contact:phone"),
            websiteUrl = extratags?.get("website") ?: extratags?.get("contact:website"),
            sourceConfidence = 0.86f,
            accessible = extratags?.get("wheelchair") == "yes" || ExploreFacet.ACCESSIBLE in facets,
            kidFriendly = when {
                type == "playground" -> true
                "school" in display_name.lowercase() -> true
                else -> null
            },
            quietScore = when {
                ExploreFacet.QUIET in facets -> 0.9f
                ExploreFacet.OUTDOORS in facets -> 0.75f
                else -> 0.45f
            },
            alcoholFocused = type == "bar" || type == "pub",
            adultOriented = false,
            recentlyOpenedOrTrending = false,
            sourceAttributions = listOf(attribution),
            providerLinks = listOf(ExploreProviderLink(providerId, place_id.toString())),
            confidenceSignals = listOfNotNull(
                extratags?.get("opening_hours")?.let {
                    ExploreConfidenceSignal(
                        label = "hours-known",
                        confidence = 0.95f,
                        inferred = false,
                        detail = "Opening hours metadata is available from Nominatim extra tags.",
                        source = attribution
                    )
                }
            ),
            whyChosenHints = listOf("Nearby provider-backed place discovery from Nominatim.")
        )
    }

    private fun inferFacets(
        category: String?,
        type: String?,
        displayName: String,
        tags: Map<String, String>?
    ): Set<ExploreFacet> {
        val tokens = listOfNotNull(category, type, displayName, tags?.values?.joinToString(" ")).joinToString(" ").lowercase()
        return buildSet {
            if (tokens.contains("restaurant") || tokens.contains("cafe") || tokens.contains("bakery") || tokens.contains("food")) add(ExploreFacet.FOOD)
            if (tokens.contains("bar") || tokens.contains("pub") || tokens.contains("brew")) add(ExploreFacet.DRINK)
            if (tokens.contains("museum") || tokens.contains("gallery") || tokens.contains("library")) add(ExploreFacet.LEARNING)
            if (tokens.contains("park") || tokens.contains("garden")) addAll(listOf(ExploreFacet.OUTDOORS, ExploreFacet.PARK, ExploreFacet.NATURE))
            if (tokens.contains("playground") || tokens.contains("kids")) addAll(listOf(ExploreFacet.KIDS, ExploreFacet.ACTIVITY))
            if (tokens.contains("market") || tokens.contains("shop") || tokens.contains("mall") || tokens.contains("supermarket")) add(ExploreFacet.SHOPPING)
            if (tokens.contains("hospital") || tokens.contains("pharmacy") || tokens.contains("town hall") || tokens.contains("government")) addAll(listOf(ExploreFacet.IMPORTANT, ExploreFacet.GOVERNMENT))
            if (tokens.contains("cinema") || tokens.contains("arcade")) add(ExploreFacet.ENTERTAINMENT)
            if (tokens.contains("lookout") || tokens.contains("scenic")) add(ExploreFacet.SCENIC)
            if (tokens.contains("quiet") || tokens.contains("library") || tokens.contains("garden")) add(ExploreFacet.QUIET)
            if (tokens.contains("wheelchair")) add(ExploreFacet.ACCESSIBLE)
        }.ifEmpty { setOf(ExploreFacet.IMPORTANT) }
    }
}

@Singleton
class NominatimPlaceDetailsProvider @Inject constructor(
    private val discoveryProvider: NominatimPlaceDiscoveryProvider
) : PlaceDetailsProvider {
    override val providerId: String = "nominatim-place-details"

    override suspend fun enrichPlaces(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, ExploreCandidate> {
        return candidates.mapNotNull { candidate ->
            val link = candidate.providerLinks.firstOrNull { it.provider == discoveryProvider.providerId } ?: return@mapNotNull null
            val dto = discoveryProvider.cachedPlace(link.externalId) ?: return@mapNotNull null
            candidate.id to candidate.copy(
                phoneNumber = candidate.phoneNumber ?: dto.extratags?.get("phone") ?: dto.extratags?.get("contact:phone"),
                websiteUrl = candidate.websiteUrl ?: dto.extratags?.get("website") ?: dto.extratags?.get("contact:website"),
                whyChosenHints = candidate.whyChosenHints + listOfNotNull(
                    dto.extratags?.get("opening_hours")?.let { "Hours metadata available from provider details." }
                )
            )
        }.toMap()
    }
}

@Singleton
class CuratedReviewProvider @Inject constructor() : ReviewProvider {
    override val providerId: String = "explore-review-catalog"

    override suspend fun reviews(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExploreReviewSourceSummary>> {
        return candidates.associate { candidate ->
            val internal = ExploreReviewSourceSummary(
                provider = "internal-community",
                providerLabel = "Simon Says GPS",
                averageRating = internalRatingFor(candidate),
                reviewCount = internalCountFor(candidate),
                summary = internalSummaryFor(candidate),
                internal = true,
                attribution = ExploreSourceAttribution(
                    provider = "internal-community",
                    label = "Simon Says GPS",
                    verified = true,
                    debugDetail = "Internal app community summary"
                ),
                confidence = 0.96f
            )
            val external = ExploreReviewSourceSummary(
                provider = providerId,
                providerLabel = "Provider summary",
                averageRating = externalRatingFor(candidate),
                reviewCount = externalCountFor(candidate),
                summary = externalSummaryFor(candidate),
                internal = false,
                attribution = ExploreSourceAttribution(
                    provider = providerId,
                    label = "Curated provider summary",
                    verified = false,
                    termsLimitedToSummary = true,
                    debugDetail = "Scaffolded summary provider"
                ),
                confidence = 0.72f
            )
            candidate.id to listOf(internal, external).sortedByDescending { it.internal }
        }
    }

    private fun internalRatingFor(candidate: ExploreCandidate): Double = when {
        ExploreFacet.QUIET in candidate.facets -> 4.8
        ExploreFacet.FOOD in candidate.facets -> 4.7
        ExploreFacet.KIDS in candidate.facets -> 4.6
        else -> 4.4
    }

    private fun internalCountFor(candidate: ExploreCandidate): Int = when {
        candidate.visitedBefore -> 14
        ExploreFacet.FOOD in candidate.facets -> 10
        else -> 4
    }

    private fun externalRatingFor(candidate: ExploreCandidate): Double = when {
        ExploreFacet.IMPORTANT in candidate.facets -> 4.2
        ExploreFacet.FOOD in candidate.facets -> 4.5
        else -> 4.3
    }

    private fun externalCountFor(candidate: ExploreCandidate): Int = when {
        ExploreFacet.SHOPPING in candidate.facets -> 180
        ExploreFacet.FOOD in candidate.facets -> 220
        else -> 72
    }

    private fun internalSummaryFor(candidate: ExploreCandidate): String = when {
        ExploreFacet.QUIET in candidate.facets -> "Internal reviewers consistently mention a calmer stop than nearby alternatives."
        ExploreFacet.FOOD in candidate.facets -> "Internal reviewers like the reliability and easy stop-in experience."
        else -> "Internal reviews are positive when this place appears in trips."
    }

    private fun externalSummaryFor(candidate: ExploreCandidate): String = when {
        ExploreFacet.SHOPPING in candidate.facets -> "Summary-only external sentiment suggests good variety and convenience."
        ExploreFacet.IMPORTANT in candidate.facets -> "Summary-only provider sentiment emphasizes usefulness over charm."
        else -> "Summary-only provider sentiment is generally favorable."
    }
}

@Singleton
class CuratedEventProvider @Inject constructor() : EventProvider {
    override val providerId: String = "explore-event-catalog"

    override suspend fun events(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExploreEventInfo>> {
        if (!query.settings.useEventDataWhenAvailable) return emptyMap()
        return candidates.mapNotNull { candidate ->
            val event = when {
                ExploreFacet.IMPORTANT in candidate.facets || ExploreFacet.LEARNING in candidate.facets -> ExploreEventInfo(
                    title = "Community spotlight",
                    startEpochMillis = query.nowEpochMillis + 30 * 60 * 1000,
                    endEpochMillis = query.nowEpochMillis + 2 * 60 * 60 * 1000,
                    summary = "Starts soon and adds timely relevance for this venue.",
                    timing = ExploreEventTiming.STARTING_SOON,
                    attribution = ExploreSourceAttribution(providerId, "Curated event feed", verified = false, debugDetail = "Scaffolded event enrichment"),
                    confidence = 0.71f
                )
                ExploreFacet.ENTERTAINMENT in candidate.facets -> ExploreEventInfo(
                    title = "Drop-in activity block",
                    startEpochMillis = query.nowEpochMillis - 15 * 60 * 1000,
                    endEpochMillis = query.nowEpochMillis + 90 * 60 * 1000,
                    summary = "Happening now.",
                    timing = ExploreEventTiming.HAPPENING_NOW,
                    attribution = ExploreSourceAttribution(providerId, "Curated event feed", verified = false),
                    confidence = 0.7f
                )
                else -> null
            }
            event?.let { candidate.id to listOf(it) }
        }.toMap()
    }
}

@Singleton
class CuratedPromotionSignalProvider @Inject constructor() : PromotionSignalProvider {
    override val providerId: String = "explore-promo-catalog"

    override suspend fun promotions(query: ExploreQuery, candidates: List<ExploreCandidate>): Map<String, List<ExplorePromotionInfo>> {
        return candidates.mapNotNull { candidate ->
            val explicit = if (ExploreFacet.SHOPPING in candidate.facets) {
                ExplorePromotionInfo(
                    summary = "Provider-backed sale summary available for nearby shopping inventory.",
                    confidence = 0.92f,
                    source = providerId,
                    attribution = ExploreSourceAttribution(providerId, "Curated promo feed", verified = true),
                    inferred = false
                )
            } else {
                null
            }
            val inferred = if (ExploreFacet.FOOD in candidate.facets || ExploreFacet.SHOPPING in candidate.facets) {
                ExplorePromotionInfo(
                    summary = "Possible special inferred from category and current Explore context.",
                    confidence = 0.58f,
                    source = providerId,
                    attribution = ExploreSourceAttribution(providerId, "Curated promo inference", verified = false, debugDetail = "Inference only; not provider-verified."),
                    inferred = true
                )
            } else {
                null
            }
            listOfNotNull(explicit, inferred).takeIf { it.isNotEmpty() }?.let { candidate.id to it }
        }.toMap()
    }
}

@Singleton
class RecentDestinationVisitHistoryProvider @Inject constructor(
    private val recentDestinationRepository: RecentDestinationRepository
) : UserVisitHistoryProvider {
    override suspend fun enrichPlaces(candidates: List<ExploreCandidate>): Map<String, ExploreCandidate> {
        val recent = recentDestinationRepository.recentDestinations.first()
        val recentByName = recent.associateBy { normalize(it.name) }
        return candidates.mapNotNull { candidate ->
            val matched = recentByName[normalize(candidate.name)] ?: return@mapNotNull null
            candidate.id to candidate.copy(
                visitedBefore = true,
                lastVisitedEpochMillis = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
                whyChosenHints = candidate.whyChosenHints + "Matched against your recent destinations.",
                confidenceSignals = candidate.confidenceSignals + ExploreConfidenceSignal(
                    label = "visited-before",
                    confidence = 0.98f,
                    inferred = false,
                    detail = "This place matches a recent destination saved in the app.",
                    source = ExploreSourceAttribution("recent-destinations", "Recent destinations", verified = true)
                )
            )
        }.toMap()
    }
}

private fun normalize(value: String): String = value.lowercase().replace("[^a-z0-9]".toRegex(), "")
