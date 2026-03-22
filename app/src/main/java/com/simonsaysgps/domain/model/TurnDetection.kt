package com.simonsaysgps.domain.model

data class TurnDetection(
    val occurred: Boolean,
    val detectedTurnType: TurnType,
    val headingDelta: Double,
    val distanceToManeuverMeters: Double,
    val onRouteCorridor: Boolean
)
