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
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OsrmRoutingRepositoryTest {
    @Test
    fun `maps osrm response into domain route`() = runTest {
        val api = object : OsrmApi {
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
        val repo = OsrmRoutingRepository(api)
        val route = repo.calculateRoute(Coordinate(40.0, -73.0), Coordinate(40.1, -72.9)).getOrThrow()
        assertThat(route.totalDistanceMeters).isEqualTo(200.0)
        assertThat(route.maneuvers.first().instruction).contains("Turn right")
    }
}
