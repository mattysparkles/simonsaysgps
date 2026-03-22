package com.simonsaysgps.domain.engine

import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.SimonTurnResolution
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
            upcomingManeuver = upcoming,
            distanceToNextManeuverMeters = upcoming?.distanceFromPreviousMeters,
            navigationActive = true
        )
    }

    fun update(
        previousState: NavigationSessionState,
        previousLocation: LocationSample?,
        currentLocation: LocationSample,
        distanceUnit: com.simonsaysgps.domain.model.DistanceUnit
    ): NavigationSessionState {
        val route = previousState.route ?: return previousState
        val currentIndex = previousState.activeManeuverIndex.coerceIn(0, route.maneuvers.lastIndex)
        val maneuver = route.maneuvers.getOrNull(currentIndex)
        if (maneuver == null) {
            return previousState.copy(
                currentLocation = currentLocation,
                navigationActive = false,
                spokenPrompt = promptFactory.approvalPrompt()
            )
        }

        val distanceToNext = GeoUtils.distanceMeters(currentLocation.coordinate, maneuver.coordinate)
        val turnDetection = turnDetector.detect(previousLocation, currentLocation, maneuver, route.geometry)
        val corridorDistance = GeoUtils.closestDistanceToPolylineMeters(currentLocation.coordinate, route.geometry)
        val offRoute = corridorDistance > 65.0
        val resolution = when {
            offRoute && distanceToNext > 50 -> SimonTurnResolution.OffRoute(maneuver)
            turnDetection.occurred && maneuver.authorization == ManeuverAuthorization.NORMAL_INFO_ONLY -> SimonTurnResolution.Unauthorized(maneuver)
            turnDetection.occurred && maneuver.authorization == ManeuverAuthorization.REQUIRED_SIMON_SAYS -> SimonTurnResolution.Authorized(maneuver)
            distanceToNext > 80 && corridorDistance > 35 && previousState.distanceToNextManeuverMeters != null && previousState.distanceToNextManeuverMeters < 35 -> SimonTurnResolution.Missed(maneuver)
            else -> SimonTurnResolution.None
        }

        val nextIndex = if (resolution is SimonTurnResolution.Authorized) currentIndex + 1 else currentIndex
        val nextManeuver = route.maneuvers.getOrNull(nextIndex)
        val prompt = when {
            resolution is SimonTurnResolution.Authorized && nextManeuver == null -> promptFactory.approvalPrompt()
            resolution is SimonTurnResolution.Unauthorized -> promptFactory.reroutePrompt(RerouteReason.UNAUTHORIZED_TURN)
            resolution is SimonTurnResolution.Missed -> promptFactory.reroutePrompt(RerouteReason.MISSED_REQUIRED_TURN)
            resolution is SimonTurnResolution.OffRoute -> promptFactory.reroutePrompt(RerouteReason.OFF_ROUTE)
            distanceToNext <= 25 -> promptFactory.immediatePrompt(maneuver)
            distanceToNext <= 150 -> promptFactory.upcomingPrompt(maneuver, distanceUnit, distanceToNext)
            else -> null
        }
        val rerouteReason = when (resolution) {
            is SimonTurnResolution.Unauthorized -> RerouteReason.UNAUTHORIZED_TURN
            is SimonTurnResolution.Missed -> RerouteReason.MISSED_REQUIRED_TURN
            is SimonTurnResolution.OffRoute -> RerouteReason.OFF_ROUTE
            else -> RerouteReason.NONE
        }
        return previousState.copy(
            currentLocation = currentLocation,
            snappedLocation = currentLocation.coordinate,
            activeManeuverIndex = nextIndex,
            distanceToNextManeuverMeters = nextManeuver?.let { GeoUtils.distanceMeters(currentLocation.coordinate, it.coordinate) },
            upcomingManeuver = nextManeuver,
            spokenPrompt = prompt,
            latestResolution = resolution,
            offRoute = resolution is SimonTurnResolution.OffRoute,
            lastRerouteReason = rerouteReason,
            headingDegrees = currentLocation.bearing?.toDouble(),
            navigationActive = nextManeuver != null
        )
    }

    fun shouldReroute(state: NavigationSessionState): Boolean = when (state.latestResolution) {
        is SimonTurnResolution.Unauthorized,
        is SimonTurnResolution.Missed,
        is SimonTurnResolution.OffRoute -> true
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
}
