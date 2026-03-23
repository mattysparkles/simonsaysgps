package com.simonsaysgps.domain.model.explore

import kotlin.math.roundToInt

enum class PlaceReviewTag(val label: String) {
    QUIET("Quiet"),
    GOOD_FOR_KIDS("Good for kids"),
    SCENIC("Scenic"),
    FUN("Fun"),
    DELICIOUS("Delicious"),
    ACCESSIBLE("Accessible"),
    CROWDED("Crowded"),
    EXPENSIVE("Expensive")
}

enum class InternalReviewModerationStatus {
    VISIBLE,
    FLAGGED,
    HIDDEN,
    REMOVED
}

data class InternalPlaceReview(
    val internalReviewId: String,
    val canonicalPlaceId: String,
    val authorDisplayName: String,
    val rating: Int,
    val reviewText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val tags: Set<PlaceReviewTag> = emptySet(),
    val visitContext: String? = null,
    val moderationStatus: InternalReviewModerationStatus = InternalReviewModerationStatus.VISIBLE,
    val source: String = "internal"
)

data class InternalReviewAggregate(
    val averageRating: Double,
    val count: Int,
    val topTags: List<PlaceReviewTag>
)

data class ExternalReviewSummaryBlock(
    val provider: String,
    val providerLabel: String,
    val averageRating: Double?,
    val reviewCount: Int,
    val summary: String? = null,
    val attribution: ExploreSourceAttribution,
    val confidence: Float,
    val excerpts: List<String> = emptyList()
)

data class PlaceDetailRecord(
    val canonicalPlaceId: String,
    val name: String,
    val typeLabel: String,
    val address: String,
    val coordinate: com.simonsaysgps.domain.model.Coordinate,
    val openNow: Boolean? = null,
    val eventInfo: ExploreEventInfo? = null,
    val distanceMeters: Double,
    val offRouteDistanceMeters: Double? = null,
    val estimatedDetourMinutes: Int? = null,
    val whyChosen: String,
    val phoneNumber: String? = null,
    val websiteUrl: String? = null,
    val hoursSummary: String? = null,
    val placeTags: List<String> = emptyList(),
    val eventSnippets: List<String> = emptyList(),
    val sourceAttributions: List<ExploreSourceAttribution> = emptyList(),
    val internalAggregate: InternalReviewAggregate? = null,
    val internalReviews: List<InternalPlaceReview> = emptyList(),
    val externalReviewSummaries: List<ExternalReviewSummaryBlock> = emptyList()
) {
    val isPartial: Boolean = phoneNumber == null || websiteUrl == null || hoursSummary == null
}

object InternalReviewAggregateCalculator {
    fun calculate(reviews: List<InternalPlaceReview>): InternalReviewAggregate? {
        val visible = reviews.filter { it.moderationStatus == InternalReviewModerationStatus.VISIBLE }
        if (visible.isEmpty()) return null
        val average = visible.map { it.rating }.average()
        val tagCounts = visible
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<PlaceReviewTag, Int>> { it.value }.thenBy { it.key.label })
            .map { it.key }
        return InternalReviewAggregate(
            averageRating = ((average * 10.0).roundToInt() / 10.0),
            count = visible.size,
            topTags = tagCounts.take(3)
        )
    }
}

object ExploreCanonicalPlaceIdFactory {
    fun fromCandidate(candidate: ExploreCandidate): String = fromParts(
        name = candidate.name,
        address = candidate.address,
        coordinate = candidate.coordinate,
        providerLinks = candidate.providerLinks
    )

    fun fromParts(
        name: String,
        address: String,
        coordinate: com.simonsaysgps.domain.model.Coordinate,
        providerLinks: List<ExploreProviderLink> = emptyList()
    ): String {
        val normalizedName = normalize(name).ifBlank { "place" }
        val normalizedAddress = normalize(address).ifBlank {
            providerLinks.sortedBy { it.provider }.joinToString("-") { "${normalize(it.provider)}-${normalize(it.externalId)}" }.ifBlank { "unknown" }
        }
        val latBucket = "%.4f".format(coordinate.latitude)
        val lonBucket = "%.4f".format(coordinate.longitude)
        return "place-$normalizedName-$normalizedAddress-$latBucket-$lonBucket"
    }

    private fun normalize(value: String): String = value.lowercase().replace("[^a-z0-9]".toRegex(), "")
}
