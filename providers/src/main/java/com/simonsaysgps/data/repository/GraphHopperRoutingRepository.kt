package com.simonsaysgps.data.repository

import com.simonsaysgps.data.remote.GraphHopperApi
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.repository.ProviderRoutingRepository
import com.simonsaysgps.domain.repository.RouteCacheStore
import com.simonsaysgps.domain.repository.RoutingProviderAvailability
import com.simonsaysgps.domain.util.GeoUtils
import javax.inject.Inject

class GraphHopperRoutingRepository @Inject constructor(
    private val api: GraphHopperApi,
    private val routeCacheStore: RouteCacheStore,
    private val configuration: RoutingProviderConfiguration,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ProviderRoutingRepository {
    override val provider: RoutingProvider = RoutingProvider.GRAPH_HOPPER

    override fun availability(): RoutingProviderAvailability {
        return if (configuration.graphHopperApiKey.isBlank()) {
            RoutingProviderAvailability(
                available = false,
                reason = "GraphHopper is selected but GRAPH_HOPPER_API_KEY is blank."
            )
        } else {
            RoutingProviderAvailability(available = true)
        }
    }

    override suspend fun calculateRoute(origin: Coordinate, destination: Coordinate): RepositoryResult<Route> {
        val availability = availability()
        if (!availability.available) {
            return RepositoryResult.Failure(
                NetworkFailureClassifier.classify(IllegalStateException(availability.reason))
            )
        }

        val originKey = RouteCacheKeyFactory.fromCoordinate(origin)
        val destinationKey = RouteCacheKeyFactory.fromCoordinate(destination)
        val cached = routeCacheStore.read(originKey, destinationKey)
        if (cached != null && clock() - cached.timestampMillis <= ROUTE_CACHE_TTL_MS) {
            return RepositoryResult.Success(cached.value, FetchSource.CACHE)
        }

        return runCatching {
            val response = api.route(
                points = listOf(
                    "${origin.latitude},${origin.longitude}",
                    "${destination.latitude},${destination.longitude}"
                ),
                profile = configuration.graphHopperProfile,
                apiKey = configuration.graphHopperApiKey
            )
            val path = response.paths.firstOrNull() ?: error("GraphHopper returned no paths")
            Route(
                geometry = path.points.coordinates.map { Coordinate(it[1], it[0]) },
                maneuvers = path.instructions.mapIndexedNotNull { index, instruction ->
                    val intervalIndex = instruction.interval.firstOrNull() ?: return@mapIndexedNotNull null
                    val point = path.points.coordinates.getOrNull(intervalIndex) ?: return@mapIndexedNotNull null
                    val turnType = mapTurnType(instruction.sign)
                    if (turnType == TurnType.DEPART) {
                        return@mapIndexedNotNull null
                    }
                    RouteManeuver(
                        id = "graphhopper-$index-${instruction.sign}",
                        coordinate = Coordinate(point[1], point[0]),
                        instruction = instruction.text,
                        turnType = turnType,
                        roadName = instruction.street_name?.takeIf { it.isNotBlank() },
                        distanceFromPreviousMeters = instruction.distance,
                        distanceToNextMeters = instruction.distance,
                        authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                        headingBefore = null,
                        headingAfter = null
                    )
                },
                totalDistanceMeters = path.distance,
                totalDurationSeconds = path.time / 1000.0,
                etaEpochSeconds = GeoUtils.etaNowPlus(path.time / 1000.0)
            )
        }.fold(
            onSuccess = { route ->
                routeCacheStore.write(originKey, destinationKey, route, clock())
                RepositoryResult.Success(route, FetchSource.NETWORK)
            },
            onFailure = { error ->
                val cachedFallback = cached ?: routeCacheStore.read(originKey, destinationKey)
                if (cachedFallback != null) {
                    RepositoryResult.Success(
                        value = cachedFallback.value,
                        source = FetchSource.CACHE,
                        fallbackFailure = NetworkFailureClassifier.classify(error)
                    )
                } else {
                    RepositoryResult.Failure(NetworkFailureClassifier.classify(error))
                }
            }
        )
    }

    private fun mapTurnType(sign: Int): TurnType = when (sign) {
        0 -> TurnType.STRAIGHT
        -1 -> TurnType.SLIGHT_LEFT
        -2 -> TurnType.LEFT
        -3 -> TurnType.SHARP_LEFT
        1 -> TurnType.SLIGHT_RIGHT
        2 -> TurnType.RIGHT
        3 -> TurnType.SHARP_RIGHT
        -98, 98 -> TurnType.UTURN
        4, 5 -> TurnType.ARRIVE
        6 -> TurnType.ROUNDABOUT
        else -> TurnType.UNKNOWN
    }

    private companion object {
        const val ROUTE_CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
