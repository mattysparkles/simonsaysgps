package com.simonsaysgps.domain.usecase

import com.simonsaysgps.domain.engine.SimonSaysEngine
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.repository.RoutingRepository
import javax.inject.Inject

class ObserveNavigationSessionUseCase @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val simonSaysEngine: SimonSaysEngine
) {
    suspend fun reroute(origin: Coordinate, destination: Coordinate, activeRoute: Route?, reason: RerouteReason): RepositoryResult<Route> {
        return when (val result = routingRepository.calculateRoute(origin, destination)) {
            is RepositoryResult.Success -> RepositoryResult.Success(
                value = if (activeRoute == null) result.value else result.value.copy(totalDurationSeconds = result.value.totalDurationSeconds),
                source = result.source,
                fallbackFailure = result.fallbackFailure
            )
            is RepositoryResult.Failure -> result
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
