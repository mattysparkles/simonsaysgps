package com.simonsaysgps.domain.usecase

import com.simonsaysgps.domain.engine.SimonSaysEngine
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.repository.RoutingRepository
import javax.inject.Inject

class ObserveNavigationSessionUseCase @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val simonSaysEngine: SimonSaysEngine
) {
    suspend fun reroute(origin: Coordinate, destination: Coordinate, activeRoute: Route?, reason: RerouteReason): Result<Route> {
        return routingRepository.calculateRoute(origin, destination).map { newRoute ->
            if (activeRoute == null) newRoute else newRoute.copy(totalDurationSeconds = newRoute.totalDurationSeconds)
        }
    }

    fun updateState(
        previousState: NavigationSessionState,
        previousLocation: LocationSample?,
        currentLocation: LocationSample,
        distanceUnit: com.simonsaysgps.domain.model.DistanceUnit,
        promptPersonality: PromptPersonality
    ): NavigationSessionState = simonSaysEngine.update(
        previousState = previousState,
        previousLocation = previousLocation,
        currentLocation = currentLocation,
        distanceUnit = distanceUnit,
        promptPersonality = promptPersonality
    )

    fun start(route: Route): NavigationSessionState = simonSaysEngine.begin(route)
}
