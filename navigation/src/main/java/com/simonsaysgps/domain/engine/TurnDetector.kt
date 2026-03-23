package com.simonsaysgps.domain.engine

import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.TurnDetection
import com.simonsaysgps.domain.model.HeadingConfidence
import com.simonsaysgps.domain.util.GeoUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

@Singleton
class TurnDetector @Inject constructor() {
    fun detect(
        previous: LocationSample?,
        current: LocationSample,
        maneuver: RouteManeuver,
        routeGeometry: List<com.simonsaysgps.domain.model.Coordinate>,
        corridorThresholdMeters: Double = 40.0
    ): TurnDetection {
        val currentBearing = current.bearing?.toDouble()
        val previousBearing = previous?.bearing?.toDouble()
        val headingConfidence = headingConfidence(previous = previous, current = current)
        val headingDelta = if (currentBearing != null && previousBearing != null) {
            GeoUtils.normalizeDelta(currentBearing - previousBearing)
        } else {
            maneuver.expectedBearingDelta ?: 0.0
        }
        val distance = GeoUtils.distanceMeters(current.coordinate, maneuver.coordinate)
        val onRouteCorridor = GeoUtils.closestDistanceToPolylineMeters(current.coordinate, routeGeometry) <= corridorThresholdMeters
        val detectedTurnType = GeoUtils.turnTypeFromDelta(headingDelta)
        val expected = maneuver.expectedBearingDelta ?: 0.0
        val matchTolerance = when (headingConfidence) {
            HeadingConfidence.HIGH -> 55.0
            HeadingConfidence.LOW -> 35.0
            HeadingConfidence.UNKNOWN -> 25.0
        }
        val matchedExpectedTurn = abs(expected) < 20 || abs(GeoUtils.normalizeDelta(headingDelta - expected)) <= matchTolerance
        val maneuverRadius = 30.0 + min(12.0, current.accuracyMeters.toDouble() * 0.4) + if (abs(expected) >= 45.0) 12.0 else 0.0
        val occurred = distance <= maneuverRadius &&
            matchedExpectedTurn &&
            (headingConfidence == HeadingConfidence.HIGH || distance <= 18.0)
        return TurnDetection(
            occurred = occurred,
            detectedTurnType = detectedTurnType,
            headingDelta = headingDelta,
            distanceToManeuverMeters = distance,
            onRouteCorridor = onRouteCorridor,
            headingConfidence = headingConfidence,
            matchedExpectedTurn = matchedExpectedTurn
        )
    }

    private fun headingConfidence(previous: LocationSample?, current: LocationSample): HeadingConfidence {
        val hasBearings = previous?.bearing != null && current.bearing != null
        if (!hasBearings) return HeadingConfidence.UNKNOWN
        val speed = min(previous.speedMetersPerSecond?.toDouble() ?: 0.0, current.speedMetersPerSecond?.toDouble() ?: 0.0)
        val accuracy = maxOf(previous.accuracyMeters.toDouble(), current.accuracyMeters.toDouble())
        return when {
            speed >= 2.2 && accuracy <= 20.0 -> HeadingConfidence.HIGH
            speed >= 0.8 -> HeadingConfidence.LOW
            else -> HeadingConfidence.UNKNOWN
        }
    }
}
