package com.simonsaysgps.domain.service

import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.util.DistanceFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PromptFactory @Inject constructor() {
    private val profiles = PromptPersonality.entries.associateWith(::profileFor)
    private val rotation = mutableMapOf<String, Int>()

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

    fun arrivalPrompt(personality: PromptPersonality): String = profile(personality).arrival()

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
                authorizedUpcoming = { instruction, distance ->
                    pick("classic-auth-upcoming",
                        "Simon says $instruction in $distance.",
                        "Simon says $instruction in $distance. Keep it tidy.",
                        "Simon says $instruction in $distance. Nice and clean.",
                        "Simon says $instruction in $distance. Make the clean move.",
                        "Simon says $instruction in $distance. Right on schedule."
                    )
                },
                informationalUpcoming = { instruction, distance ->
                    pick("classic-info-upcoming",
                        "$instruction in $distance.",
                        "$instruction in $distance. Stay with the route.",
                        "$instruction in $distance. Smooth and simple.",
                        "$instruction in $distance. Keep the line neat.",
                        "$instruction in $distance. Nice easy setup here."
                    )
                },
                authorizedImmediate = { instruction ->
                    pick("classic-auth-now",
                        "Simon says $instruction.",
                        "Simon says $instruction now.",
                        "Simon says $instruction. Keep the streak alive."
                    )
                },
                informationalImmediate = { instruction ->
                    pick("classic-info-now",
                        "$instruction.",
                        "$instruction now.",
                        "$instruction. Nice and easy."
                    )
                },
                approval = { pick("classic-approval", "Nice job. Simon approves.", "Well played. Simon approves.", "Clean move. Simon approves.") },
                arrival = { pick("classic-arrival", "You have arrived. Simon approves.", "Destination reached. Simon approves.", "You made it. Simon approves.") },
                unauthorizedTurn = { pick("classic-unauth", "Oh, Simon didn't say. Rerouting.", "Simon definitely did not call that turn. Rerouting.", "That was not a Simon says turn. Recalculating.") },
                missedTurn = { pick("classic-missed", "Simon noticed that miss. Rerouting.", "Missed the turn. Simon is recalculating.", "That turn got away from us. Rerouting.") },
                offRoute = { pick("classic-offroute", "Off route. Simon is recalculating.", "You drifted off route. Simon is recalculating.", "Route broken. Simon is stitching it back together.") },
                manualReroute = { pick("classic-manual", "Rerouting now.", "New route coming up.", "Simon is drawing a cleaner line now.") }
            )
            PromptPersonality.SNARKY_SIMON -> PromptPersonalityProfile(
                authorizedUpcoming = { instruction, distance ->
                    pick("snarky-auth-upcoming",
                        "Simon says $instruction in $distance. Keep up.",
                        "Simon says $instruction in $distance. Let us not overcomplicate this.",
                        "Simon says $instruction in $distance. A rare chance to look coordinated.",
                        "Simon says $instruction in $distance. If you nail this one, I might brag about you.",
                        "Simon says $instruction in $distance. You're only $distance away from proving me wrong."
                    )
                },
                informationalUpcoming = { instruction, distance ->
                    pick("snarky-info-upcoming",
                        "$instruction in $distance. If you're into good ideas.",
                        "$instruction in $distance. Just a thought from the competent side of the windshield.",
                        "$instruction in $distance. This would be a great moment to listen.",
                        "$instruction in $distance. Tiny distance, huge opportunity.",
                        "$instruction in $distance. If we did this trip enough times we'd become a cautionary tale."
                    )
                },
                authorizedImmediate = { instruction ->
                    pick("snarky-auth-now",
                        "Simon says $instruction. Try not to improvise.",
                        "Simon says $instruction. No bonus chaos, please.",
                        "Simon says $instruction. Let us surprise everyone by nailing it.",
                        "Simon says $instruction. This is the part where we pretend we're professionals.",
                        "Simon says $instruction. Make it look deliberate."
                    )
                },
                informationalImmediate = { instruction ->
                    pick("snarky-info-now",
                        "$instruction. Or don't, but that's on you.",
                        "$instruction. I can only make this so easy.",
                        "$instruction. Please resist your little side quest instincts."
                    )
                },
                approval = { pick("snarky-approval", "Nice. Simon is almost impressed.", "That was competent. Suspiciously competent.", "Good. Simon can work with that.") },
                arrival = { pick("snarky-arrival", "You made it. Simon will allow it.", "Destination reached. Against the odds, nice work.", "Look at that. We arrived without a dramatic subplot.", "Trip complete. If you made every right turn, I remain deeply conflicted.", "Destination reached. Not bad for a species that invented traffic.") },
                unauthorizedTurn = { pick("snarky-unauth", "Simon definitely didn't say to do that. Rerouting.", "That was not a Simon says turn. Bold, but wrong. Rerouting.", "Interesting freestyle choice. Simon is fixing it.") },
                missedTurn = { pick("snarky-missed", "Simon called that turn. You missed it. Rerouting.", "That turn had your name on it. Apparently unreadable. Rerouting.", "Missed it. Cool. Simon is recalculating.", "We were supposed to turn back there. The road noticed. Rerouting.", "Missed the turn. If we did that 22,003 times, we'd at least get a story out of it.") },
                offRoute = { pick("snarky-offroute", "Bold choice. You're off route, so Simon is recalculating.", "We are off route now. Love that for us. Recalculating.", "You wandered. Simon is dragging this back on script.", "Off route. Excellent commitment to the side quest, but I am fixing it.", "We're improvising now. Simon is converting this back into transportation.") },
                manualReroute = { pick("snarky-manual", "Rerouting. Since we're making up the route now.", "New route coming in. Let us pretend this was the plan.", "Rerouting. I too enjoy doing things twice.", "Fresh route loading. New plan, same suspicious confidence.", "Rerouting. The map and I are having a quick strategy meeting.") }
            )
            PromptPersonality.POLITE_SIMON -> PromptPersonalityProfile(
                authorizedUpcoming = { instruction, distance ->
                    pick("polite-auth-upcoming",
                        "Simon says please $instruction in $distance.",
                        "Simon says please $instruction in $distance when it is safe.",
                        "Simon says $instruction in $distance, please and thank you."
                    )
                },
                informationalUpcoming = { instruction, distance ->
                    pick("polite-info-upcoming",
                        "Please $instruction in $distance.",
                        "$instruction in $distance, please.",
                        "When you are ready, please $instruction in $distance."
                    )
                },
                authorizedImmediate = { instruction ->
                    pick("polite-auth-now",
                        "Simon says please $instruction.",
                        "Simon says $instruction now, please.",
                        "Simon says please $instruction when safe."
                    )
                },
                informationalImmediate = { instruction ->
                    pick("polite-info-now",
                        "Please $instruction.",
                        "$instruction now, please.",
                        "Please $instruction when safe."
                    )
                },
                approval = { pick("polite-approval", "Nicely done. Simon appreciates that.", "Lovely work. Simon appreciates the calm driving.", "Thank you. Simon appreciated that move.") },
                arrival = { pick("polite-arrival", "You have arrived. Simon appreciates the careful driving.", "Destination reached. Thank you for the careful drive.", "You made it. Simon appreciates the smooth finish.") },
                unauthorizedTurn = { pick("polite-unauth", "Simon didn't say to turn there. Rerouting now, please.", "That turn was not in the plan. Simon will reroute now.", "We turned a bit early there. Simon is recalculating now.") },
                missedTurn = { pick("polite-missed", "It looks like that Simon says turn was missed. Rerouting now.", "We seem to have missed the turn. Simon will correct the route now.", "That turn slipped by. Simon is preparing a new route.") },
                offRoute = { pick("polite-offroute", "We seem to be off route. Simon will recalculate, please hold on.", "We have drifted off route a bit. Simon is recalculating now.", "Route adjustment in progress. Thank you for your patience.") },
                manualReroute = { pick("polite-manual", "Rerouting now, thank you.", "One moment please, Simon is finding a new route.", "Preparing a fresh route now. Thank you.") }
            )
        }
    }

    private fun pick(key: String, vararg options: String): String {
        val lastIndex = rotation[key] ?: -1
        val nextIndex = if (options.size <= 1) {
            0
        } else {
            generateSequence { Random.nextInt(options.size) }.first { it != lastIndex }
        }
        rotation[key] = nextIndex
        return options[nextIndex]
    }
}

private data class PromptPersonalityProfile(
    val authorizedUpcoming: (instruction: String, distance: String) -> String,
    val informationalUpcoming: (instruction: String, distance: String) -> String,
    val authorizedImmediate: (instruction: String) -> String,
    val informationalImmediate: (instruction: String) -> String,
    val approval: () -> String,
    val arrival: () -> String,
    val unauthorizedTurn: () -> String,
    val missedTurn: () -> String,
    val offRoute: () -> String,
    val manualReroute: () -> String
)
