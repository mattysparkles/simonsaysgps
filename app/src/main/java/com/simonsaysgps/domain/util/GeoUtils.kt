package com.simonsaysgps.domain.util

import com.simonsaysgps.domain.model.Coordinate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val hav = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(hav), sqrt(1 - hav))
    }

    fun bearingDegrees(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun normalizeDelta(delta: Double): Double {
        var normalized = delta
        while (normalized > 180) normalized -= 360
        while (normalized < -180) normalized += 360
        return normalized
    }

    fun turnTypeFromDelta(delta: Double): com.simonsaysgps.domain.model.TurnType {
        val normalized = normalizeDelta(delta)
        val absDelta = abs(normalized)
        return when {
            absDelta < 20 -> com.simonsaysgps.domain.model.TurnType.STRAIGHT
            normalized in 20.0..60.0 -> com.simonsaysgps.domain.model.TurnType.SLIGHT_RIGHT
            normalized > 60.0 && normalized <= 135.0 -> com.simonsaysgps.domain.model.TurnType.RIGHT
            normalized > 135.0 -> com.simonsaysgps.domain.model.TurnType.SHARP_RIGHT
            normalized in -60.0..-20.0 -> com.simonsaysgps.domain.model.TurnType.SLIGHT_LEFT
            normalized < -60.0 && normalized >= -135.0 -> com.simonsaysgps.domain.model.TurnType.LEFT
            normalized < -135.0 -> com.simonsaysgps.domain.model.TurnType.SHARP_LEFT
            else -> com.simonsaysgps.domain.model.TurnType.UNKNOWN
        }
    }

    fun closestDistanceToPolylineMeters(point: Coordinate, polyline: List<Coordinate>): Double {
        if (polyline.size < 2) return Double.MAX_VALUE
        return polyline.zipWithNext { a, b ->
            minOf(distanceMeters(point, a), distanceMeters(point, b), midpointDistance(point, a, b))
        }.minOrNull() ?: Double.MAX_VALUE
    }

    private fun midpointDistance(point: Coordinate, a: Coordinate, b: Coordinate): Double {
        val mid = Coordinate((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2)
        return distanceMeters(point, mid)
    }

    fun etaNowPlus(durationSeconds: Double): Long = (System.currentTimeMillis() / 1000L) + durationSeconds.roundToInt()
}
