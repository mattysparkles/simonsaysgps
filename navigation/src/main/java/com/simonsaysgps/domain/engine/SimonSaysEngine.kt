package com.simonsaysgps.domain.engine

import com.simonsaysgps.domain.model.ArrivalStatus
import com.simonsaysgps.domain.model.HeadingConfidence
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationDebugInfo
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.SimonTurnResolution
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.service.PromptFactory
import com.simonsaysgps.domain.util.GeoUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimonSaysEngine @Inject constructor(
    private val turnDetector: TurnDetector,
    private val promptFactory: PromptFactory
) {
    fun begin(route: Route): NavigationSessionState {
        val upcoming = route.maneuvers.firstOrNull()
        return NavigationSessionState(
            route = route,
            currentRoad = upcoming?.roadName,
            upcomingManeuver = upcoming,
            distanceToNextManeuverMeters = upcoming?.distanceFromPreviousMeters,
            arrivalStatus = if (upcoming?.turnType == TurnType.ARRIVE) ArrivalStatus.APPROACHING_DESTINATION else ArrivalStatus.EN_ROUTE,
            navigationActive = true,
            debugInfo = NavigationDebugInfo(
                activeStepDistanceMeters = upcoming?.distanceFromPreviousMeters,
                lastTransitionReason = "navigation-started"
            )
        )
    }

    fun update(
        previousState: NavigationSessionState,
        previousLocation: LocationSample?,
        currentLocation: LocationSample,
        distanceUnit: com.simonsaysgps.domain.model.DistanceUnit,
        promptPersonality: PromptPersonality
    ): NavigationSessionState {
        val route = previousState.route ?: return previousState
        val currentIndex = previousState.activeManeuverIndex.coerceIn(0, route.maneuvers.lastIndex)
        val maneuver = route.maneuvers.getOrNull(currentIndex)
        if (maneuver == null) {
            return previousState.copy(
                currentLocation = currentLocation,
                currentRoad = null,
                arrivalStatus = ArrivalStatus.ARRIVED,
                navigationActive = false,
                spokenPrompt = promptFactory.arrivalPrompt(promptPersonality)
            )
        }

        if (previousState.arrivalStatus == ArrivalStatus.ARRIVED) {
            return previousState.copy(
                currentLocation = currentLocation,
                snappedLocation = currentLocation.coordinate,
                headingDegrees = currentLocation.bearing?.toDouble(),
                spokenPrompt = null,
                latestResolution = SimonTurnResolution.None,
                lastRerouteReason = RerouteReason.NONE,
                debugInfo = previousState.debugInfo.copy(
                    activeStepDistanceMeters = 0.0,
                    lastTransitionReason = "arrival-latched"
                )
            )
        }

        val now = currentLocation.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val distanceToNext = GeoUtils.distanceMeters(currentLocation.coordinate, maneuver.coordinate)
        val currentProjection = GeoUtils.projectOntoPolyline(currentLocation.coordinate, route.geometry)
        val maneuverProjection = GeoUtils.projectOntoPolyline(maneuver.coordinate, route.geometry)
        val corridorThreshold = corridorThresholdMeters(currentLocation, distanceToNext, previousState.distanceToNextManeuverMeters)
        val turnDetection = turnDetector.detect(previousLocation, currentLocation, maneuver, route.geometry, corridorThreshold)
        val corridorDistance = currentProjection.distanceMeters
        val previousDistance = previousState.distanceToNextManeuverMeters
        val isNearIntersection = distanceToNext <= INTERSECTION_APPROACH_THRESHOLD_METERS ||
            (previousDistance != null && previousDistance <= INTERSECTION_APPROACH_THRESHOLD_METERS + 10.0)
        val intersectionGraceUntilMillis = updatedIntersectionGrace(previousState, now, distanceToNext, isNearIntersection)
        val inIntersectionGrace = now < intersectionGraceUntilMillis
        val passedManeuver = GeoUtils.hasPassedProjection(currentProjection, maneuverProjection) ||
            (previousDistance != null && previousDistance <= 25.0 && distanceToNext >= previousDistance + 18.0)
        val rerouteCooldownActive = now < previousState.rerouteCooldownUntilMillis
        val stepLockActive = now < previousState.stepProgressionLockUntilMillis
        val currentSpeed = currentLocation.speedMetersPerSecond?.toDouble() ?: 0.0
        val arrivedAtDestination = maneuver.turnType == TurnType.ARRIVE && (
            distanceToNext <= ARRIVAL_THRESHOLD_METERS ||
                (distanceToNext <= ARRIVAL_LATCH_THRESHOLD_METERS && currentSpeed <= ARRIVAL_LATCH_MAX_SPEED_MPS)
            )

        val unauthorizedCandidate = turnDetection.occurred &&
            maneuver.authorization == ManeuverAuthorization.NORMAL_INFO_ONLY &&
            turnDetection.onRouteCorridor
        val authorizedCandidate = turnDetection.occurred &&
            maneuver.authorization == ManeuverAuthorization.REQUIRED_SIMON_SAYS
        val missedCandidate = maneuver.authorization == ManeuverAuthorization.REQUIRED_SIMON_SAYS &&
            passedManeuver &&
            previousDistance != null &&
            previousDistance <= MISSED_TURN_ENTRY_THRESHOLD_METERS &&
            distanceToNext >= previousDistance + MISSED_TURN_DISTANCE_INCREASE_METERS &&
            !turnDetection.onRouteCorridor
        val offRouteCandidate = corridorDistance > corridorThreshold + OFF_ROUTE_EXTRA_BUFFER_METERS &&
            distanceToNext > OFF_ROUTE_MIN_MANEUVER_DISTANCE_METERS &&
            !arrivedAtDestination

        val suppressionReason = when {
            rerouteCooldownActive -> "reroute-cooldown"
            stepLockActive && isNearIntersection -> "post-step-lock"
            inIntersectionGrace && turnDetection.onRouteCorridor && turnDetection.headingConfidence != HeadingConfidence.HIGH -> "intersection-heading-grace"
            maneuver.turnType == TurnType.ARRIVE && distanceToNext <= ARRIVAL_LATCH_THRESHOLD_METERS -> "arrival-latch"
            else -> null
        }

        val resolution = when {
            arrivedAtDestination -> SimonTurnResolution.Authorized(maneuver)
            suppressionReason != null -> SimonTurnResolution.None
            unauthorizedCandidate -> SimonTurnResolution.Unauthorized(maneuver)
            authorizedCandidate -> SimonTurnResolution.Authorized(maneuver)
            missedCandidate -> SimonTurnResolution.Missed(maneuver)
            offRouteCandidate -> SimonTurnResolution.OffRoute(maneuver)
            else -> SimonTurnResolution.None
        }

        val nextIndex = if (resolution is SimonTurnResolution.Authorized) currentIndex + 1 else currentIndex
        val nextManeuver = route.maneuvers.getOrNull(nextIndex)
        val arrivalStatus = when {
            nextManeuver == null && resolution is SimonTurnResolution.Authorized -> ArrivalStatus.ARRIVED
            nextManeuver?.turnType == TurnType.ARRIVE -> ArrivalStatus.APPROACHING_DESTINATION
            maneuver.turnType == TurnType.ARRIVE && distanceToNext <= APPROACHING_DESTINATION_THRESHOLD_METERS -> ArrivalStatus.APPROACHING_DESTINATION
            else -> ArrivalStatus.EN_ROUTE
        }
        val prompt = when {
            resolution is SimonTurnResolution.Authorized && nextManeuver == null -> promptFactory.arrivalPrompt(promptPersonality)
            resolution is SimonTurnResolution.Unauthorized -> promptFactory.reroutePrompt(RerouteReason.UNAUTHORIZED_TURN, promptPersonality)
            resolution is SimonTurnResolution.Missed -> promptFactory.reroutePrompt(RerouteReason.MISSED_REQUIRED_TURN, promptPersonality)
            resolution is SimonTurnResolution.OffRoute -> promptFactory.reroutePrompt(RerouteReason.OFF_ROUTE, promptPersonality)
            distanceToNext <= 25 -> promptFactory.immediatePrompt(maneuver, promptPersonality)
            distanceToNext <= 150 -> promptFactory.upcomingPrompt(maneuver, distanceUnit, distanceToNext, promptPersonality)
            else -> null
        }
        val rerouteReason = when (resolution) {
            is SimonTurnResolution.Unauthorized -> RerouteReason.UNAUTHORIZED_TURN
            is SimonTurnResolution.Missed -> RerouteReason.MISSED_REQUIRED_TURN
            is SimonTurnResolution.OffRoute -> RerouteReason.OFF_ROUTE
            else -> RerouteReason.NONE
        }
        val nextStepLockUntilMillis = when (resolution) {
            is SimonTurnResolution.Authorized -> if (nextManeuver != null) now + STEP_PROGRESSION_LOCK_MILLIS else previousState.stepProgressionLockUntilMillis
            else -> previousState.stepProgressionLockUntilMillis
        }
        val nextRerouteCooldownUntilMillis = when (resolution) {
            is SimonTurnResolution.Unauthorized,
            is SimonTurnResolution.Missed,
            is SimonTurnResolution.OffRoute -> now + REROUTE_COOLDOWN_MILLIS
            else -> previousState.rerouteCooldownUntilMillis
        }
        val corridorStatus = when {
            turnDetection.onRouteCorridor && inIntersectionGrace -> "intersection-grace"
            turnDetection.onRouteCorridor -> "on-corridor"
            else -> "outside-corridor"
        }
        val transitionReason = when (resolution) {
            is SimonTurnResolution.Authorized -> if (maneuver.turnType == TurnType.ARRIVE) "arrival-confirmed" else "authorized-turn"
            is SimonTurnResolution.Unauthorized -> "unauthorized-turn-detected"
            is SimonTurnResolution.Missed -> "passed-required-turn"
            is SimonTurnResolution.OffRoute -> "corridor-exit"
            SimonTurnResolution.None -> suppressionReason ?: "tracking-active"
        }
        return previousState.copy(
            currentLocation = currentLocation,
            snappedLocation = currentProjection.snappedCoordinate,
            activeManeuverIndex = nextIndex,
            distanceToNextManeuverMeters = nextManeuver?.let { GeoUtils.distanceMeters(currentLocation.coordinate, it.coordinate) },
            currentRoad = nextManeuver?.roadName ?: maneuver.roadName,
            upcomingManeuver = nextManeuver,
            spokenPrompt = prompt,
            latestResolution = resolution,
            offRoute = resolution is SimonTurnResolution.OffRoute,
            lastRerouteReason = rerouteReason,
            headingDegrees = currentLocation.bearing?.toDouble(),
            arrivalStatus = arrivalStatus,
            navigationActive = nextManeuver != null,
            intersectionGraceUntilMillis = intersectionGraceUntilMillis,
            rerouteCooldownUntilMillis = nextRerouteCooldownUntilMillis,
            stepProgressionLockUntilMillis = nextStepLockUntilMillis,
            debugInfo = NavigationDebugInfo(
                activeStepDistanceMeters = distanceToNext,
                routeCorridorDistanceMeters = corridorDistance,
                routeCorridorThresholdMeters = corridorThreshold,
                routeCorridorStatus = corridorStatus,
                headingConfidence = turnDetection.headingConfidence,
                hysteresisState = when {
                    rerouteCooldownActive -> "reroute-cooldown"
                    stepLockActive -> "step-progression-lock"
                    inIntersectionGrace -> "intersection-grace"
                    else -> "idle"
                },
                rerouteSuppressionReason = suppressionReason,
                lastTransitionReason = transitionReason
            )
        )
    }

    fun shouldReroute(state: NavigationSessionState): Boolean = when (state.latestResolution) {
        is SimonTurnResolution.Unauthorized,
        is SimonTurnResolution.Missed,
        is SimonTurnResolution.OffRoute -> state.debugInfo.rerouteSuppressionReason == null
        else -> false
    }

    fun assignAuthorizations(route: Route, mode: com.simonsaysgps.domain.model.GameMode): Route {
        val updated = route.maneuvers.mapIndexed { index, maneuver ->
            val authorization = when (mode) {
                com.simonsaysgps.domain.model.GameMode.BASIC -> ManeuverAuthorization.REQUIRED_SIMON_SAYS
                com.simonsaysgps.domain.model.GameMode.MISCHIEF -> if (index % 2 == 0) ManeuverAuthorization.REQUIRED_SIMON_SAYS else ManeuverAuthorization.NORMAL_INFO_ONLY
            }
            maneuver.copy(authorization = authorization)
        }
        return route.copy(maneuvers = updated)
    }

    private companion object {
        const val APPROACHING_DESTINATION_THRESHOLD_METERS = 75.0
        const val ARRIVAL_THRESHOLD_METERS = 20.0
        const val ARRIVAL_LATCH_THRESHOLD_METERS = 30.0
        const val ARRIVAL_LATCH_MAX_SPEED_MPS = 2.5
        const val INTERSECTION_APPROACH_THRESHOLD_METERS = 32.0
        const val INTERSECTION_GRACE_MILLIS = 6_000L
        const val MISSED_TURN_ENTRY_THRESHOLD_METERS = 28.0
        const val MISSED_TURN_DISTANCE_INCREASE_METERS = 24.0
        const val OFF_ROUTE_MIN_MANEUVER_DISTANCE_METERS = 45.0
        const val OFF_ROUTE_EXTRA_BUFFER_METERS = 12.0
        const val REROUTE_COOLDOWN_MILLIS = 8_000L
        const val STEP_PROGRESSION_LOCK_MILLIS = 6_000L
    }

    private fun corridorThresholdMeters(
        currentLocation: LocationSample,
        distanceToNext: Double,
        previousDistance: Double?
    ): Double {
        val accuracyAllowance = (currentLocation.accuracyMeters.toDouble() * 0.8).coerceIn(4.0, 18.0)
        val nearIntersection = distanceToNext <= INTERSECTION_APPROACH_THRESHOLD_METERS ||
            (previousDistance != null && previousDistance <= INTERSECTION_APPROACH_THRESHOLD_METERS + 10.0)
        val base = if (nearIntersection) 44.0 else 28.0
        return (base + accuracyAllowance).coerceAtMost(if (nearIntersection) 60.0 else 46.0)
    }

    private fun updatedIntersectionGrace(
        previousState: NavigationSessionState,
        now: Long,
        distanceToNext: Double,
        isNearIntersection: Boolean
    ): Long {
        return when {
            isNearIntersection -> maxOf(previousState.intersectionGraceUntilMillis, now + INTERSECTION_GRACE_MILLIS)
            now < previousState.intersectionGraceUntilMillis && distanceToNext <= APPROACHING_DESTINATION_THRESHOLD_METERS -> previousState.intersectionGraceUntilMillis
            else -> 0L
        }
    }
}
