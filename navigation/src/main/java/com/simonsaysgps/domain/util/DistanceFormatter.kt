package com.simonsaysgps.domain.util

import com.simonsaysgps.domain.model.DistanceUnit
import kotlin.math.roundToInt

object DistanceFormatter {
    fun format(distanceMeters: Double, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.IMPERIAL -> {
                val feet = distanceMeters * 3.28084
                if (feet < 1000) "${feet.roundToInt()} feet" else String.format("%.1f miles", distanceMeters / 1609.34)
            }
            DistanceUnit.METRIC -> {
                if (distanceMeters < 1000) "${distanceMeters.roundToInt()} meters" else String.format("%.1f km", distanceMeters / 1000)
            }
        }
    }
}
