package com.simonsaysgps.domain.model

data class LocationSample(
    val coordinate: Coordinate,
    val accuracyMeters: Float,
    val bearing: Float?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long
)
