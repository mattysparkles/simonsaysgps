package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.data.remote.OsrmApi
import com.simonsaysgps.data.remote.OsrmGeometryDto
import com.simonsaysgps.data.remote.OsrmLegDto
import com.simonsaysgps.data.remote.OsrmManeuverDto
import com.simonsaysgps.data.remote.OsrmRouteDto
import com.simonsaysgps.data.remote.OsrmRouteResponse
import com.simonsaysgps.data.remote.OsrmStepDto
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.NetworkFailureType
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.repository.CachedValue
import com.simonsaysgps.domain.repository.RouteCacheStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.SocketTimeoutException

class OsrmRoutingRepositoryTest {
    @Test
    fun `maps osrm response into domain route`() = runTest {
        val api = successfulApi()
        val repo = OsrmRoutingRepository(api, FakeRouteCacheStore())
        val result = repo.calculateRoute(Coordinate(40.0, -73.0), Coordinate(40.1, -72.9)) as RepositoryResult.Success
        val route = result.value

        assertThat(result.source).isEqualTo(FetchSource.NETWORK)
        assertThat(route.totalDistanceMeters).isEqualTo(200.0)
        assertThat(route.maneuvers.first().instruction).contains("Turn right")
    }

    @Test
    fun `falls back to cached route after timeout`() = runTest {
        val origin = Coordinate(40.0, -73.0)
        val destination = Coordinate(40.1, -72.9)
        val cache = FakeRouteCacheStore()
        val cachedRoute = sampleRoute()
        cache.write(
            RouteCacheKeyFactory.fromCoordinate(origin),
            RouteCacheKeyFactory.fromCoordinate(destination),
            cachedRoute,
            0L
        )
        val api = object : OsrmApi {
            override suspend fun route(
                coordinates: String,
                alternatives: Boolean,
                overview: String,
                geometries: String,
                steps: Boolean,
                annotations: Boolean
            ): OsrmRouteResponse {
                throw SocketTimeoutException("slow")
            }
        }
        val repo = OsrmRoutingRepository(api, cache) { 10 * 60 * 1000L }

        val result = repo.calculateRoute(origin, destination) as RepositoryResult.Success

        assertThat(result.source).isEqualTo(FetchSource.CACHE)
        assertThat(result.fallbackFailure?.type).isEqualTo(NetworkFailureType.TIMEOUT)
        assertThat(result.value).isEqualTo(cachedRoute)
    }

    private fun successfulApi() = object : OsrmApi {
        override suspend fun route(
            coordinates: String,
            alternatives: Boolean,
            overview: String,
            geometries: String,
            steps: Boolean,
            annotations: Boolean
        ): OsrmRouteResponse = OsrmRouteResponse(
            code = "Ok",
            routes = listOf(
                OsrmRouteDto(
                    geometry = OsrmGeometryDto(listOf(listOf(-73.0, 40.0), listOf(-72.9, 40.1))),
                    distance = 200.0,
                    duration = 180.0,
                    legs = listOf(
                        OsrmLegDto(
                            steps = listOf(
                                OsrmStepDto(
                                    distance = 30.0,
                                    duration = 20.0,
                                    name = "Pond Road",
                                    geometry = OsrmGeometryDto(emptyList()),
                                    maneuver = OsrmManeuverDto(
                                        location = listOf(-73.0, 40.0),
                                        type = "turn",
                                        modifier = "right",
                                        bearing_before = 0,
                                        bearing_after = 90
                                    ),
                                    mode = "driving"
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun sampleRoute() = Route(
        geometry = listOf(Coordinate(40.0, -73.0), Coordinate(40.1, -72.9)),
        maneuvers = emptyList(),
        totalDistanceMeters = 100.0,
        totalDurationSeconds = 90.0,
        etaEpochSeconds = 1234L
    )

    private class FakeRouteCacheStore : RouteCacheStore {
        private val cache = mutableMapOf<String, CachedValue<Route>>()

        override suspend fun read(originKey: String, destinationKey: String): CachedValue<Route>? = cache["$originKey->$destinationKey"]

        override suspend fun write(originKey: String, destinationKey: String, route: Route, timestampMillis: Long) {
            cache["$originKey->$destinationKey"] = CachedValue(route, timestampMillis)
        }
    }
}
