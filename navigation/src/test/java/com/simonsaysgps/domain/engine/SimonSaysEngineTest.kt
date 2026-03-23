package com.simonsaysgps.domain.engine

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.ArrivalStatus
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.HeadingConfidence
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
            previousLocation = sample(0.0, 0.0, 0f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.0001, 90f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )
        assertThat(updated.latestResolution).isInstanceOf(SimonTurnResolution.Unauthorized::class.java)
        assertThat(updated.rerouteCooldownUntilMillis).isGreaterThan(2_000L)
    }

    @Test
    fun `missed turn is detected after passing required maneuver corridor`() {
        val route = route()
        val start = NavigationSessionState(
            route = route,
            activeManeuverIndex = 0,
            distanceToNextManeuverMeters = 20.0,
            upcomingManeuver = route.maneuvers.first(),
            navigationActive = true,
            currentLocation = sample(0.0, 0.00008, 0f, timestampMillis = 1_000L)
        )
        val updated = engine.update(
            previousState = start,
            previousLocation = sample(0.0, 0.00008, 0f, timestampMillis = 1_000L),
            currentLocation = sample(0.00045, 0.00045, 0f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )
        assertThat(updated.latestResolution).isInstanceOf(SimonTurnResolution.Missed::class.java)
        assertThat(engine.shouldReroute(updated)).isTrue()
    }

    @Test
    fun `authorized maneuver advances to next step`() {
        val route = route()
        val start = engine.begin(route)
        val updated = engine.update(
            previousState = start.copy(distanceToNextManeuverMeters = 10.0),
            previousLocation = sample(0.0, 0.0, 0f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.0001, 90f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )
        assertThat(updated.activeManeuverIndex).isEqualTo(1)
        assertThat(updated.stepProgressionLockUntilMillis).isGreaterThan(2_000L)
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
            previousLocation = sample(0.0, 0.0, 0f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.00005, 0f, speed = 1f, timestampMillis = 2_000L),
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
            previousLocation = sample(0.0, 0.0, 0f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.0, 0f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.POLITE_SIMON
        )

        assertThat(updated.spokenPrompt).isEqualTo("Simon says please turn right onto pond road.")
    }

    @Test
    fun `near intersection jitter is suppressed while still on corridor`() {
        val route = route()
        val start = engine.begin(route).copy(
            distanceToNextManeuverMeters = 18.0,
            intersectionGraceUntilMillis = 0L
        )

        val updated = engine.update(
            previousState = start,
            previousLocation = sample(0.0, 0.00007, 0f, speed = 1f, accuracy = 14f, timestampMillis = 1_000L),
            currentLocation = sample(0.00001, 0.000085, 18f, speed = 1f, accuracy = 14f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        assertThat(updated.latestResolution).isEqualTo(SimonTurnResolution.None)
        assertThat(updated.debugInfo.rerouteSuppressionReason).isEqualTo("intersection-heading-grace")
        assertThat(updated.debugInfo.headingConfidence).isEqualTo(HeadingConfidence.LOW)
        assertThat(engine.shouldReroute(updated)).isFalse()
    }

    @Test
    fun `slow approach to valid turn does not trigger missed turn`() {
        val route = route()
        val start = engine.begin(route).copy(distanceToNextManeuverMeters = 22.0)

        val updated = engine.update(
            previousState = start,
            previousLocation = sample(0.0, 0.00006, 0f, speed = 0.6f, accuracy = 10f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.000085, 8f, speed = 0.7f, accuracy = 10f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        assertThat(updated.latestResolution).isEqualTo(SimonTurnResolution.None)
        assertThat(updated.lastRerouteReason).isEqualTo(com.simonsaysgps.domain.model.RerouteReason.NONE)
    }

    @Test
    fun `false reroute is suppressed during cooldown`() {
        val route = route()
        val start = engine.begin(route).copy(
            distanceToNextManeuverMeters = 80.0,
            rerouteCooldownUntilMillis = 10_000L
        )

        val updated = engine.update(
            previousState = start,
            previousLocation = sample(0.0, 0.0001, 90f, timestampMillis = 1_000L),
            currentLocation = sample(0.0007, 0.0007, 90f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        assertThat(updated.latestResolution).isEqualTo(SimonTurnResolution.None)
        assertThat(updated.debugInfo.rerouteSuppressionReason).isEqualTo("reroute-cooldown")
        assertThat(engine.shouldReroute(updated)).isFalse()
    }

    @Test
    fun `arrival latch prevents reroute after effective arrival`() {
        val route = Route(
            geometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.0002)),
            maneuvers = listOf(
                RouteManeuver(
                    id = "arrive",
                    coordinate = Coordinate(0.0, 0.0002),
                    instruction = "You have arrived",
                    turnType = TurnType.ARRIVE,
                    roadName = "Destination",
                    distanceFromPreviousMeters = 20.0,
                    distanceToNextMeters = 0.0,
                    authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                    headingBefore = 0.0,
                    headingAfter = 0.0
                )
            ),
            totalDistanceMeters = 20.0,
            totalDurationSeconds = 20.0,
            etaEpochSeconds = 1L
        )
        val started = engine.begin(route)
        val arrived = engine.update(
            previousState = started,
            previousLocation = sample(0.0, 0.00016, 0f, speed = 1f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.00019, 0f, speed = 1f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )
        val latched = engine.update(
            previousState = arrived,
            previousLocation = sample(0.0, 0.00019, 0f, speed = 0.5f, timestampMillis = 2_000L),
            currentLocation = sample(0.00002, 0.000185, 180f, speed = 0.3f, accuracy = 12f, timestampMillis = 3_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        assertThat(arrived.arrivalStatus).isEqualTo(ArrivalStatus.ARRIVED)
        assertThat(latched.arrivalStatus).isEqualTo(ArrivalStatus.ARRIVED)
        assertThat(latched.latestResolution).isEqualTo(SimonTurnResolution.None)
        assertThat(engine.shouldReroute(latched)).isFalse()
    }

    @Test
    fun `step progression lock suppresses adjacent step thrash`() {
        val route = route()
        val afterAuthorized = engine.update(
            previousState = engine.begin(route).copy(distanceToNextManeuverMeters = 10.0),
            previousLocation = sample(0.0, 0.0, 0f, timestampMillis = 1_000L),
            currentLocation = sample(0.0, 0.0001, 90f, timestampMillis = 2_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        val updated = engine.update(
            previousState = afterAuthorized,
            previousLocation = sample(0.0, 0.0001, 90f, speed = 1f, accuracy = 12f, timestampMillis = 2_500L),
            currentLocation = sample(0.00004, 0.000115, 100f, speed = 1f, accuracy = 12f, timestampMillis = 3_000L),
            distanceUnit = DistanceUnit.IMPERIAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON
        )

        assertThat(updated.latestResolution).isEqualTo(SimonTurnResolution.None)
        assertThat(updated.debugInfo.rerouteSuppressionReason).isEqualTo("post-step-lock")
        assertThat(engine.shouldReroute(updated)).isFalse()
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

    private fun sample(
        lat: Double,
        lon: Double,
        bearing: Float,
        speed: Float = 4f,
        accuracy: Float = 5f,
        timestampMillis: Long = 0L
    ) = LocationSample(
        coordinate = Coordinate(lat, lon),
        accuracyMeters = accuracy,
        bearing = bearing,
        speedMetersPerSecond = speed,
        timestampMillis = timestampMillis
    )
}
