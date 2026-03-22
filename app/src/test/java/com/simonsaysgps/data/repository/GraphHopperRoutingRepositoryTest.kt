package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.data.remote.GraphHopperApi
import com.simonsaysgps.data.remote.GraphHopperInstructionDto
import com.simonsaysgps.data.remote.GraphHopperPathDto
import com.simonsaysgps.data.remote.GraphHopperPointsDto
import com.simonsaysgps.data.remote.GraphHopperRouteResponse
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.repository.CachedValue
import com.simonsaysgps.domain.repository.RouteCacheStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GraphHopperRoutingRepositoryTest {
    @Test
    fun `maps graphhopper response into domain route`() = runTest {
        val repository = GraphHopperRoutingRepository(
            api = successfulApi(),
            routeCacheStore = FakeRouteCacheStore(),
            configuration = RoutingProviderConfiguration(
                defaultProvider = com.simonsaysgps.domain.model.RoutingProvider.OSRM,
                graphHopperApiKey = "test-key",
                graphHopperProfile = "car",
                valhallaBaseUrl = ""
            )
        )

        val result = repository.calculateRoute(Coordinate(40.0, -73.0), Coordinate(40.1, -72.9)) as RepositoryResult.Success

        assertThat(result.source).isEqualTo(FetchSource.NETWORK)
        assertThat(result.value.totalDistanceMeters).isEqualTo(250.0)
        assertThat(result.value.maneuvers.first().turnType).isEqualTo(TurnType.RIGHT)
        assertThat(result.value.maneuvers.first().instruction).contains("Turn right")
    }

    @Test
    fun `reports unavailable when graphhopper key missing`() {
        val repository = GraphHopperRoutingRepository(
            api = successfulApi(),
            routeCacheStore = FakeRouteCacheStore(),
            configuration = RoutingProviderConfiguration(
                defaultProvider = com.simonsaysgps.domain.model.RoutingProvider.OSRM,
                graphHopperApiKey = "",
                graphHopperProfile = "car",
                valhallaBaseUrl = ""
            )
        )

        assertThat(repository.availability().available).isFalse()
        assertThat(repository.availability().reason).contains("GRAPH_HOPPER_API_KEY")
    }

    private fun successfulApi() = object : GraphHopperApi {
        override suspend fun route(
            points: List<String>,
            profile: String,
            instructions: Boolean,
            calcPoints: Boolean,
            pointsEncoded: Boolean,
            apiKey: String
        ): GraphHopperRouteResponse = GraphHopperRouteResponse(
            paths = listOf(
                GraphHopperPathDto(
                    distance = 250.0,
                    time = 180_000,
                    points = GraphHopperPointsDto(
                        coordinates = listOf(
                            listOf(-73.0, 40.0),
                            listOf(-72.95, 40.05),
                            listOf(-72.9, 40.1)
                        )
                    ),
                    instructions = listOf(
                        GraphHopperInstructionDto(
                            distance = 50.0,
                            text = "Turn right onto Pond Road",
                            street_name = "Pond Road",
                            sign = 2,
                            interval = listOf(1, 2),
                            time = 60_000
                        )
                    )
                )
            )
        )
    }

    private class FakeRouteCacheStore : RouteCacheStore {
        private val cache = mutableMapOf<String, CachedValue<com.simonsaysgps.domain.model.Route>>()

        override suspend fun read(originKey: String, destinationKey: String): CachedValue<com.simonsaysgps.domain.model.Route>? = cache["$originKey->$destinationKey"]

        override suspend fun write(
            originKey: String,
            destinationKey: String,
            route: com.simonsaysgps.domain.model.Route,
            timestampMillis: Long
        ) {
            cache["$originKey->$destinationKey"] = CachedValue(route, timestampMillis)
        }
    }
}
