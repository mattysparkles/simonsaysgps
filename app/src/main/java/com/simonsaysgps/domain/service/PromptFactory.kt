package com.simonsaysgps.domain.service

import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.util.DistanceFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptFactory @Inject constructor() {
    private val profiles = PromptPersonality.entries.associateWith(::profileFor)

    fun upcomingPrompt(
        maneuver: RouteManeuver,
        unit: DistanceUnit,
        distanceMeters: Double,
        personality: PromptPersonality
    ): String {
        val formattedDistance = DistanceFormatter.format(distanceMeters, unit)
        val profile = profile(personality)
        return when (maneuver.authorization) {
            ManeuverAuthorization.REQUIRED_SIMON_SAYS ->
                profile.authorizedUpcoming(maneuver.instruction.lowercase(), formattedDistance)
            ManeuverAuthorization.NORMAL_INFO_ONLY ->
                profile.informationalUpcoming(maneuver.instruction, formattedDistance)
        }
    }

    fun immediatePrompt(maneuver: RouteManeuver, personality: PromptPersonality): String {
        val profile = profile(personality)
        return when (maneuver.authorization) {
            ManeuverAuthorization.REQUIRED_SIMON_SAYS ->
                profile.authorizedImmediate(maneuver.instruction.lowercase())
            ManeuverAuthorization.NORMAL_INFO_ONLY ->
                profile.informationalImmediate(maneuver.instruction)
        }
    }

    fun approvalPrompt(personality: PromptPersonality): String = profile(personality).approval()

    fun reroutePrompt(reason: RerouteReason, personality: PromptPersonality): String {
        return when (reason) {
            RerouteReason.UNAUTHORIZED_TURN -> profile(personality).unauthorizedTurn()
            RerouteReason.MISSED_REQUIRED_TURN -> profile(personality).missedTurn()
            RerouteReason.OFF_ROUTE -> profile(personality).offRoute()
            RerouteReason.MANUAL -> profile(personality).manualReroute()
            RerouteReason.NONE -> ""
        }
    }

    private fun profile(personality: PromptPersonality): PromptPersonalityProfile {
        return profiles.getValue(personality)
    }

    private fun profileFor(personality: PromptPersonality): PromptPersonalityProfile {
        return when (personality) {
            PromptPersonality.CLASSIC_SIMON -> PromptPersonalityProfile(
                authorizedUpcoming = { instruction, distance -> "Simon says $instruction in $distance." },
                informationalUpcoming = { instruction, distance -> "$instruction in $distance." },
                authorizedImmediate = { instruction -> "Simon says $instruction." },
                informationalImmediate = { instruction -> "$instruction." },
                approval = { "Nice job. Simon approves." },
                unauthorizedTurn = { "Oh, Simon didn't say. Rerouting." },
                missedTurn = { "Simon noticed that miss. Rerouting." },
                offRoute = { "Off route. Simon is recalculating." },
                manualReroute = { "Rerouting now." }
            )
            PromptPersonality.SNARKY_SIMON -> PromptPersonalityProfile(
                authorizedUpcoming = { instruction, distance -> "Simon says $instruction in $distance. Keep up." },
                informationalUpcoming = { instruction, distance -> "$instruction in $distance. If you're into good ideas." },
                authorizedImmediate = { instruction -> "Simon says $instruction. Try not to improvise." },
                informationalImmediate = { instruction -> "$instruction. Or don't, but that's on you." },
                approval = { "Nice. Simon is almost impressed." },
                unauthorizedTurn = { "Simon definitely didn't say to do that. Rerouting." },
                missedTurn = { "Simon called that turn. You missed it. Rerouting." },
                offRoute = { "Bold choice. You're off route, so Simon is recalculating." },
                manualReroute = { "Rerouting. Since we're making up the route now." }
            )
            PromptPersonality.POLITE_SIMON -> PromptPersonalityProfile(
                authorizedUpcoming = { instruction, distance -> "Simon says please $instruction in $distance." },
                informationalUpcoming = { instruction, distance -> "Please $instruction in $distance." },
                authorizedImmediate = { instruction -> "Simon says please $instruction." },
                informationalImmediate = { instruction -> "Please $instruction." },
                approval = { "Nicely done. Simon appreciates that." },
                unauthorizedTurn = { "Simon didn't say to turn there. Rerouting now, please." },
                missedTurn = { "It looks like that Simon says turn was missed. Rerouting now." },
                offRoute = { "We seem to be off route. Simon will recalculate, please hold on." },
                manualReroute = { "Rerouting now, thank you." }
            )
        }
    }
}

private data class PromptPersonalityProfile(
    val authorizedUpcoming: (instruction: String, distance: String) -> String,
    val informationalUpcoming: (instruction: String, distance: String) -> String,
    val authorizedImmediate: (instruction: String) -> String,
    val informationalImmediate: (instruction: String) -> String,
    val approval: () -> String,
    val unauthorizedTurn: () -> String,
    val missedTurn: () -> String,
    val offRoute: () -> String,
    val manualReroute: () -> String
)
