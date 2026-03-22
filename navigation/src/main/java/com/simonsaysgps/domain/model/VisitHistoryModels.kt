package com.simonsaysgps.domain.model

import com.simonsaysgps.domain.model.explore.ExploreProviderLink

enum class VisitObservationSource {
    APP_CONFIRMED_ARRIVAL,
    APP_CONFIRMED_SAVE,
    APP_OBSERVED
}

data class VisitHistoryEntry(
    val id: String,
    val placeId: String,
    val name: String,
    val address: String,
    val coordinate: Coordinate,
    val visitedAtEpochMillis: Long,
    val confidence: Float,
    val source: VisitObservationSource,
    val providerLinks: List<ExploreProviderLink> = emptyList(),
    val notes: String? = null
)
