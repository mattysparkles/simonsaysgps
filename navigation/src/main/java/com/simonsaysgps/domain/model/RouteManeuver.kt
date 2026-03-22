package com.simonsaysgps.domain.model

data class RouteManeuver(
    val id: String,
    val coordinate: Coordinate,
    val instruction: String,
    val turnType: TurnType,
    val roadName: String?,
    val distanceFromPreviousMeters: Double,
    val distanceToNextMeters: Double,
    val authorization: ManeuverAuthorization,
    val headingBefore: Double?,
    val headingAfter: Double?,
    val expectedBearingDelta: Double? = headingBefore?.let { before -> headingAfter?.let { after -> normalizeDelta(after - before) } }
) {
    companion object {
        private fun normalizeDelta(delta: Double): Double {
            var normalized = delta
            while (normalized > 180) normalized -= 360
            while (normalized < -180) normalized += 360
            return normalized
        }
    }
}
