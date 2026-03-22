package com.simonsaysgps.domain.service

import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.util.DistanceFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptFactory @Inject constructor() {
    fun upcomingPrompt(maneuver: RouteManeuver, unit: DistanceUnit, distanceMeters: Double): String {
        val formattedDistance = DistanceFormatter.format(distanceMeters, unit)
        val base = when (maneuver.authorization) {
            ManeuverAuthorization.REQUIRED_SIMON_SAYS -> "Simon says ${maneuver.instruction.lowercase()}"
            ManeuverAuthorization.NORMAL_INFO_ONLY -> maneuver.instruction
        }
        return "$base in $formattedDistance."
    }

    fun immediatePrompt(maneuver: RouteManeuver): String {
        return when (maneuver.authorization) {
            ManeuverAuthorization.REQUIRED_SIMON_SAYS -> "Simon says ${maneuver.instruction.lowercase()}."
            ManeuverAuthorization.NORMAL_INFO_ONLY -> maneuver.instruction
        }
    }

    fun approvalPrompt(): String = "Nice job. Simon approves."

    fun reroutePrompt(reason: RerouteReason): String = when (reason) {
        RerouteReason.UNAUTHORIZED_TURN -> "Oh, Simon didn't say. Rerouting."
        RerouteReason.MISSED_REQUIRED_TURN -> "Simon noticed that miss. Rerouting."
        RerouteReason.OFF_ROUTE -> "Off route. Simon is recalculating."
        RerouteReason.MANUAL -> "Rerouting now."
        RerouteReason.NONE -> ""
    }
}
