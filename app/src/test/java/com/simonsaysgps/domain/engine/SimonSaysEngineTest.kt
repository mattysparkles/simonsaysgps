package com.simonsaysgps.domain.engine

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.SimonTurnResolution
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.service.PromptFactory
import org.junit.Test

class SimonSaysEngineTest {
    private val engine = SimonSaysEngine(TurnDetector(), PromptFactory())

    @Test
    fun `basic mode marks every maneuver as simon says`() {
        val updated = engine.assignAuthorizations(route(), GameMode.BASIC)
        assertThat(updated.maneuvers.map { it.authorization }.distinct()).containsExactly(ManeuverAuthorization.REQUIRED_SIMON_SAYS)
    }

    @Test
    fun `mischief mode alternates authorization`() {
        val updated = engine.assignAuthorizations(route(), GameMode.MISCHIEF)
        assertThat(updated.maneuvers[0].authorization).isEqualTo(ManeuverAuthorization.REQUIRED_SIMON_SAYS)
        assertThat(updated.maneuvers[1].authorization).isEqualTo(ManeuverAuthorization.NORMAL_INFO_ONLY)
    }

    @Test
    fun `unauthorized turn triggers reroute resolution`() {
        val route = route().copy(maneuvers = listOf(maneuver(ManeuverAuthorization.NORMAL_INFO_ONLY)))
        val start = engine.begin(route)
        val updated = engine.update(
            previousState = start.copy(distanceToNextManeuverMeters = 10.0),
            previousLocation = sample(0.0, 0.0, 0f),
            currentLocation = sample(0.0, 0.0001, 90f),
            distanceUnit = DistanceUnit.IMPERIAL
        )
        assertThat(updated.latestResolution).isInstanceOf(SimonTurnResolution.Unauthorized::class.java)
    }

    @Test
    fun `missed turn is detected after passing maneuver corridor`() {
        val route = route()
        val start = NavigationSessionState(
            route = route,
            activeManeuverIndex = 0,
            distanceToNextManeuverMeters = 20.0,
            upcomingManeuver = route.maneuvers.first(),
            navigationActive = true
        )
        val updated = engine.update(start, sample(0.0, 0.0, 0f), sample(0.002, 0.002, 0f), DistanceUnit.IMPERIAL)
        assertThat(updated.latestResolution).isInstanceOf(SimonTurnResolution.OffRoute::class.java)
    }

    @Test
    fun `authorized maneuver advances to next step`() {
        val route = route()
        val start = engine.begin(route)
        val updated = engine.update(start.copy(distanceToNextManeuverMeters = 10.0), sample(0.0, 0.0, 0f), sample(0.0, 0.0001, 90f), DistanceUnit.IMPERIAL)
        assertThat(updated.activeManeuverIndex).isEqualTo(1)
    }

    private fun route() = Route(
        geometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.0001), Coordinate(0.0001, 0.0001)),
        maneuvers = listOf(
            maneuver(ManeuverAuthorization.REQUIRED_SIMON_SAYS),
            maneuver(ManeuverAuthorization.REQUIRED_SIMON_SAYS).copy(id = "two", coordinate = Coordinate(0.0001, 0.0001))
        ),
        totalDistanceMeters = 100.0,
        totalDurationSeconds = 80.0,
        etaEpochSeconds = 1L
    )

    private fun maneuver(auth: ManeuverAuthorization) = RouteManeuver(
        id = "one",
        coordinate = Coordinate(0.0, 0.0001),
        instruction = "Turn right onto Pond Road",
        turnType = TurnType.RIGHT,
        roadName = "Pond Road",
        distanceFromPreviousMeters = 20.0,
        distanceToNextMeters = 20.0,
        authorization = auth,
        headingBefore = 0.0,
        headingAfter = 90.0
    )

    private fun sample(lat: Double, lon: Double, bearing: Float) = LocationSample(
        coordinate = Coordinate(lat, lon),
        accuracyMeters = 5f,
        bearing = bearing,
        speedMetersPerSecond = 4f,
        timestampMillis = 0L
    )
}
