package com.simonsaysgps.domain.model

data class Route(
    val geometry: List<Coordinate>,
    val maneuvers: List<RouteManeuver>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val etaEpochSeconds: Long
)
