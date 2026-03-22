package com.simonsaysgps.data.repository

import com.simonsaysgps.data.remote.OsrmApi
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.util.GeoUtils
import javax.inject.Inject

class OsrmRoutingRepository @Inject constructor(
    private val api: OsrmApi
) : RoutingRepository {
    override suspend fun calculateRoute(origin: Coordinate, destination: Coordinate): Result<Route> = runCatching {
        val coordinates = "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}"
        val response = api.route(coordinates)
        require(response.code == "Ok") { "OSRM returned ${response.code}" }
        val route = response.routes.first()
        val maneuvers = route.legs.flatMap { leg ->
            leg.steps.mapIndexed { index, step ->
                RouteManeuver(
                    id = "step-$index-${step.name}",
                    coordinate = Coordinate(step.maneuver.location[1], step.maneuver.location[0]),
                    instruction = buildInstruction(step),
                    turnType = mapTurnType(step.maneuver.modifier, step.maneuver.type),
                    roadName = step.name.takeIf { it.isNotBlank() },
                    distanceFromPreviousMeters = step.distance,
                    distanceToNextMeters = step.distance,
                    authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                    headingBefore = step.maneuver.bearing_before?.toDouble(),
                    headingAfter = step.maneuver.bearing_after?.toDouble()
                )
            }
        }.filterNot { it.turnType == TurnType.DEPART }
        Route(
            geometry = route.geometry.coordinates.map { Coordinate(it[1], it[0]) },
            maneuvers = maneuvers,
            totalDistanceMeters = route.distance,
            totalDurationSeconds = route.duration,
            etaEpochSeconds = GeoUtils.etaNowPlus(route.duration)
        )
    }

    private fun buildInstruction(step: com.simonsaysgps.data.remote.OsrmStepDto): String {
        val type = mapTurnType(step.maneuver.modifier, step.maneuver.type)
        val onto = step.name.takeIf { it.isNotBlank() }?.let { " onto $it" } ?: ""
        return when (type) {
            TurnType.LEFT, TurnType.SLIGHT_LEFT, TurnType.SHARP_LEFT -> "Turn left$onto"
            TurnType.RIGHT, TurnType.SLIGHT_RIGHT, TurnType.SHARP_RIGHT -> "Turn right$onto"
            TurnType.UTURN -> "Make a U-turn$onto"
            TurnType.ARRIVE -> "You have arrived"
            TurnType.STRAIGHT -> "Continue straight$onto"
            else -> step.maneuver.type.replaceFirstChar { it.uppercase() } + onto
        }
    }

    private fun mapTurnType(modifier: String?, type: String): TurnType = when {
        type == "depart" -> TurnType.DEPART
        type == "arrive" -> TurnType.ARRIVE
        type == "roundabout" -> TurnType.ROUNDABOUT
        modifier == "slight left" -> TurnType.SLIGHT_LEFT
        modifier == "left" -> TurnType.LEFT
        modifier == "sharp left" -> TurnType.SHARP_LEFT
        modifier == "slight right" -> TurnType.SLIGHT_RIGHT
        modifier == "right" -> TurnType.RIGHT
        modifier == "sharp right" -> TurnType.SHARP_RIGHT
        modifier == "uturn" -> TurnType.UTURN
        modifier == "straight" -> TurnType.STRAIGHT
        else -> TurnType.UNKNOWN
    }
}
