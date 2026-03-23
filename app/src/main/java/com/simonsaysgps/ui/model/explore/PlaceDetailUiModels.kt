package com.simonsaysgps.ui.model.explore

import com.simonsaysgps.domain.model.explore.ExternalReviewSummaryBlock
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.PlaceDetailRecord
import com.simonsaysgps.domain.model.explore.PlaceReviewTag

enum class PlaceDetailStatus {
    IDLE,
    LOADING,
    PARTIAL,
    READY,
    EMPTY,
    ERROR
}

data class PlaceDetailUiState(
    val status: PlaceDetailStatus = PlaceDetailStatus.IDLE,
    val seedPlaceId: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val address: String = "",
    val statusLine: String = "",
    val distanceLine: String = "",
    val whyChosen: String = "",
    val phoneNumber: String? = null,
    val websiteUrl: String? = null,
    val hoursSummary: String? = null,
    val tagLabels: List<String> = emptyList(),
    val eventSnippets: List<String> = emptyList(),
    val internalRatingSummary: String? = null,
    val externalRatingSummary: String? = null,
    val internalReviews: List<InternalPlaceReview> = emptyList(),
    val externalReviewSummaries: List<ExternalReviewSummaryBlock> = emptyList(),
    val sourceLabels: List<String> = emptyList(),
    val isSaved: Boolean = false,
    val savedSummary: String? = null,
    val errorMessage: String? = null,
    val helperMessage: String? = null
)

data class ReviewComposeUiState(
    val canonicalPlaceId: String? = null,
    val placeName: String = "",
    val existingReviewId: String? = null,
    val rating: Int = 0,
    val reviewText: String = "",
    val selectedTags: Set<PlaceReviewTag> = emptySet(),
    val saving: Boolean = false,
    val validationError: String? = null,
    val successMessage: String? = null,
    val helperMessage: String? = null
) {
    val isEditing: Boolean = existingReviewId != null
}

object PlaceDetailUiStateFactory {
    fun loading(placeId: String?) = PlaceDetailUiState(status = PlaceDetailStatus.LOADING, seedPlaceId = placeId)

    fun error(placeId: String?, message: String) = PlaceDetailUiState(
        status = PlaceDetailStatus.ERROR,
        seedPlaceId = placeId,
        errorMessage = message
    )

    fun empty(message: String) = PlaceDetailUiState(
        status = PlaceDetailStatus.EMPTY,
        helperMessage = message,
        errorMessage = null
    )

    fun fromRecord(record: PlaceDetailRecord): PlaceDetailUiState {
        val internalSummary = record.internalAggregate?.let {
            buildString {
                append("${"%.1f".format(it.averageRating)}★ from ${it.count} internal review")
                if (it.count != 1) append('s')
                if (it.topTags.isNotEmpty()) append(" · ${it.topTags.joinToString { tag -> tag.label }}")
            }
        }
        val externalSummary = record.externalReviewSummaries.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ") { block ->
            buildString {
                append(block.providerLabel)
                block.averageRating?.let { append(" ${"%.1f".format(it)}★") }
                append(" (${block.reviewCount})")
            }
        }
        return PlaceDetailUiState(
            status = if (record.isPartial) PlaceDetailStatus.PARTIAL else PlaceDetailStatus.READY,
            seedPlaceId = record.canonicalPlaceId,
            title = record.name,
            subtitle = record.typeLabel,
            address = record.address,
            statusLine = record.eventInfo?.summary
                ?: when (record.openNow) {
                    true -> "Open now"
                    false -> "Closed right now"
                    null -> "Status unavailable"
                },
            distanceLine = record.offRouteDistanceMeters?.let { offRoute ->
                buildString {
                    append(formatMiles(offRoute))
                    append(" miles off route")
                    record.estimatedDetourMinutes?.let { append(" · about $it min detour") }
                }
            } ?: "${formatMiles(record.distanceMeters)} miles away",
            whyChosen = record.whyChosen,
            phoneNumber = record.phoneNumber,
            websiteUrl = record.websiteUrl,
            hoursSummary = record.hoursSummary,
            tagLabels = record.placeTags,
            eventSnippets = record.eventSnippets,
            internalRatingSummary = internalSummary,
            externalRatingSummary = externalSummary,
            internalReviews = record.internalReviews,
            externalReviewSummaries = record.externalReviewSummaries,
            sourceLabels = record.sourceAttributions.map { it.label },
            isSaved = record.savedPlace != null,
            savedSummary = record.savedPlace?.let { "Saved locally on this device." },
            helperMessage = if (record.isPartial) "Showing the best provider data currently available for this place." else null
        )
    }

    private fun formatMiles(distanceMeters: Double): String = "%.1f".format(distanceMeters / 1609.34)
}

object ReviewComposeValidator {
    fun validate(rating: Int, reviewText: String): String? {
        if (rating !in 1..5) return "Choose a rating before submitting your review."
        if (reviewText.trim().length < 10) return "Write at least 10 characters so the review is useful to other drivers."
        return null
    }
}
