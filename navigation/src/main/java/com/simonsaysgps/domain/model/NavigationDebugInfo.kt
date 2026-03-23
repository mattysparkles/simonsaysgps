package com.simonsaysgps.domain.model

data class NavigationDebugInfo(
    val activeStepDistanceMeters: Double? = null,
    val routeCorridorDistanceMeters: Double? = null,
    val routeCorridorThresholdMeters: Double? = null,
    val routeCorridorStatus: String = "unknown",
    val headingConfidence: HeadingConfidence = HeadingConfidence.UNKNOWN,
    val hysteresisState: String = "idle",
    val rerouteSuppressionReason: String? = null,
    val lastTransitionReason: String = "none"
)
