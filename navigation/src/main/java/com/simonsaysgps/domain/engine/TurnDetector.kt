package com.simonsaysgps.domain.engine

import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.TurnDetection
import com.simonsaysgps.domain.util.GeoUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class TurnDetector @Inject constructor() {
    fun detect(previous: LocationSample?, current: LocationSample, maneuver: RouteManeuver, routeGeometry: List<com.simonsaysgps.domain.model.Coordinate>): TurnDetection {
        val currentBearing = current.bearing?.toDouble()
        val previousBearing = previous?.bearing?.toDouble()
        val headingDelta = if (currentBearing != null && previousBearing != null) {
            GeoUtils.normalizeDelta(currentBearing - previousBearing)
        } else {
            maneuver.expectedBearingDelta ?: 0.0
        }
        val distance = GeoUtils.distanceMeters(current.coordinate, maneuver.coordinate)
        val onRouteCorridor = GeoUtils.closestDistanceToPolylineMeters(current.coordinate, routeGeometry) <= 40.0
        val detectedTurnType = GeoUtils.turnTypeFromDelta(headingDelta)
        val expected = maneuver.expectedBearingDelta ?: 0.0
        val occurred = distance <= 45.0 && (abs(expected) < 20 || abs(GeoUtils.normalizeDelta(headingDelta - expected)) <= 45.0)
        return TurnDetection(
            occurred = occurred,
            detectedTurnType = detectedTurnType,
            headingDelta = headingDelta,
            distanceToManeuverMeters = distance,
            onRouteCorridor = onRouteCorridor
        )
    }
}
