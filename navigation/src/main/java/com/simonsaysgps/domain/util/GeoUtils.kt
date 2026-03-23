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
    private const val METERS_PER_DEGREE_LAT = 111_320.0

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
        return projectOntoPolyline(point, polyline).distanceMeters
    }

    fun projectOntoPolyline(point: Coordinate, polyline: List<Coordinate>): PolylineProjection {
        if (polyline.size < 2) {
            return PolylineProjection(
                distanceMeters = Double.MAX_VALUE,
                snappedCoordinate = polyline.firstOrNull() ?: point,
                segmentIndex = 0,
                segmentFraction = 0.0
            )
        }
        val projectedPoint = toProjectedPoint(point, point.latitude)
        var best = PolylineProjection(
            distanceMeters = Double.MAX_VALUE,
            snappedCoordinate = polyline.first(),
            segmentIndex = 0,
            segmentFraction = 0.0
        )
        polyline.zipWithNext().forEachIndexed { index, (a, b) ->
            val referenceLatitude = (a.latitude + b.latitude + point.latitude) / 3.0
            val aPoint = toProjectedPoint(a, referenceLatitude)
            val bPoint = toProjectedPoint(b, referenceLatitude)
            val dx = bPoint.x - aPoint.x
            val dy = bPoint.y - aPoint.y
            val segmentLengthSquared = dx * dx + dy * dy
            val rawFraction = if (segmentLengthSquared == 0.0) 0.0 else {
                ((projectedPoint.x - aPoint.x) * dx + (projectedPoint.y - aPoint.y) * dy) / segmentLengthSquared
            }
            val fraction = rawFraction.coerceIn(0.0, 1.0)
            val snappedX = aPoint.x + dx * fraction
            val snappedY = aPoint.y + dy * fraction
            val distance = sqrt((projectedPoint.x - snappedX).pow(2) + (projectedPoint.y - snappedY).pow(2))
            if (distance < best.distanceMeters) {
                best = PolylineProjection(
                    distanceMeters = distance,
                    snappedCoordinate = fromProjectedPoint(ProjectedPoint(snappedX, snappedY), referenceLatitude),
                    segmentIndex = index,
                    segmentFraction = fraction
                )
            }
        }
        return best
    }

    fun hasPassedProjection(current: PolylineProjection, target: PolylineProjection, fractionTolerance: Double = 0.05): Boolean {
        return current.segmentIndex > target.segmentIndex ||
            (current.segmentIndex == target.segmentIndex && current.segmentFraction >= target.segmentFraction + fractionTolerance)
    }

    fun etaNowPlus(durationSeconds: Double): Long = (System.currentTimeMillis() / 1000L) + durationSeconds.roundToInt()

    private fun toProjectedPoint(coordinate: Coordinate, referenceLatitude: Double): ProjectedPoint {
        val latRadians = Math.toRadians(referenceLatitude)
        val x = coordinate.longitude * METERS_PER_DEGREE_LAT * cos(latRadians)
        val y = coordinate.latitude * METERS_PER_DEGREE_LAT
        return ProjectedPoint(x = x, y = y)
    }

    private fun fromProjectedPoint(point: ProjectedPoint, referenceLatitude: Double): Coordinate {
        val latRadians = Math.toRadians(referenceLatitude)
        val latitude = point.y / METERS_PER_DEGREE_LAT
        val longitude = point.x / (METERS_PER_DEGREE_LAT * cos(latRadians))
        return Coordinate(latitude = latitude, longitude = longitude)
    }

    data class PolylineProjection(
        val distanceMeters: Double,
        val snappedCoordinate: Coordinate,
        val segmentIndex: Int,
        val segmentFraction: Double
    )

    private data class ProjectedPoint(
        val x: Double,
        val y: Double
    )
}
