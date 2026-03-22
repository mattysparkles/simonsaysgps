package com.simonsaysgps.domain.engine

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.ArrivalStatus
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.PromptPersonality
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
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
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
        val updated = engine.update(
            previousState = start,
            previousLocation = sample(0.0, 0.0, 0f),
            currentLocation = sample(0.002, 0.002, 0f),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )
        assertThat(updated.latestResolution).isInstanceOf(SimonTurnResolution.OffRoute::class.java)
    }

    @Test
    fun `authorized maneuver advances to next step`() {
        val route = route()
        val start = engine.begin(route)
        val updated = engine.update(
            previousState = start.copy(distanceToNextManeuverMeters = 10.0),
            previousLocation = sample(0.0, 0.0, 0f),
            currentLocation = sample(0.0, 0.0001, 90f),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )
        assertThat(updated.activeManeuverIndex).isEqualTo(1)
    }


    @Test
    fun `arrival maneuver transitions from approaching to arrived`() {
        val route = Route(
            geometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.00005)),
            maneuvers = listOf(
                RouteManeuver(
                    id = "arrive",
                    coordinate = Coordinate(0.0, 0.00005),
                    instruction = "You have arrived",
                    turnType = TurnType.ARRIVE,
                    roadName = "Destination",
                    distanceFromPreviousMeters = 10.0,
                    distanceToNextMeters = 0.0,
                    authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                    headingBefore = 0.0,
                    headingAfter = 0.0
                )
            ),
            totalDistanceMeters = 10.0,
            totalDurationSeconds = 10.0,
            etaEpochSeconds = 1L
        )

        val started = engine.begin(route)
        val updated = engine.update(
            previousState = started,
            previousLocation = sample(0.0, 0.0, 0f),
            currentLocation = sample(0.0, 0.00005, 0f),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        assertThat(started.arrivalStatus).isEqualTo(ArrivalStatus.APPROACHING_DESTINATION)
        assertThat(updated.arrivalStatus).isEqualTo(ArrivalStatus.ARRIVED)
        assertThat(updated.navigationActive).isFalse()
        assertThat(updated.spokenPrompt).isEqualTo("You have arrived. Simon approves.")
    }

    @Test
    fun `selected personality changes spoken prompt text`() {
        val route = route()
        val start = engine.begin(route)

        val updated = engine.update(
            previousState = start.copy(distanceToNextManeuverMeters = 10.0),
            previousLocation = sample(0.0, 0.0, 0f),
            currentLocation = sample(0.0, 0.0, 0f),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.POLITE_SIMON
        )

        assertThat(updated.spokenPrompt).isEqualTo("Simon says please turn right onto pond road.")
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
