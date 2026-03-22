package com.simonsaysgps.domain.model

enum class LaneDirection {
    SHARP_LEFT,
    LEFT,
    SLIGHT_LEFT,
    STRAIGHT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    UTURN,
    UNKNOWN
}

enum class LanePreference {
    RECOMMENDED,
    AVAILABLE,
    NOT_RECOMMENDED
}

data class LaneIndicator(
    val directions: Set<LaneDirection>,
    val preference: LanePreference = LanePreference.AVAILABLE
)

data class LaneGuidance(
    val lanes: List<LaneIndicator>,
    val providerSupplied: Boolean = true
)
