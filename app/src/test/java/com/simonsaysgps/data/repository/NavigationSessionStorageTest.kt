package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.SimonTurnResolution
import com.simonsaysgps.domain.model.TurnType
import com.squareup.moshi.Moshi
import org.junit.Test

class NavigationSessionStorageTest {
    private val adapter = Moshi.Builder().build().adapter(StoredNavigationSessionState::class.java)

    @Test
    fun `encode and decode preserves restorable navigation fields`() {
        val route = Route(
            geometry = listOf(Coordinate(37.0, -122.0), Coordinate(37.1, -122.1)),
            maneuvers = listOf(
                RouteManeuver(
                    id = "maneuver-1",
                    coordinate = Coordinate(37.1, -122.1),
                    instruction = "Turn right onto Market Street",
                    turnType = TurnType.RIGHT,
                    roadName = "Market Street",
                    distanceFromPreviousMeters = 40.0,
                    distanceToNextMeters = 0.0,
                    authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                    headingBefore = 0.0,
                    headingAfter = 90.0
                )
            ),
            totalDistanceMeters = 120.0,
            totalDurationSeconds = 75.0,
            etaEpochSeconds = 1234L
        )
        val state = NavigationSessionState(
            route = route,
            currentLocation = LocationSample(
                coordinate = Coordinate(37.0, -122.0),
                accuracyMeters = 5f,
                bearing = 90f,
                speedMetersPerSecond = 4f,
                timestampMillis = 99L
            ),
            snappedLocation = Coordinate(37.0, -122.0),
            activeManeuverIndex = 0,
            distanceToNextManeuverMeters = 10.0,
            currentRoad = "Main Street",
            upcomingManeuver = route.maneuvers.first(),
            spokenPrompt = "Simon says turn right",
            latestResolution = SimonTurnResolution.OffRoute(route.maneuvers.first()),
            offRoute = true,
            lastRerouteReason = RerouteReason.OFF_ROUTE,
            headingDegrees = 90.0,
            navigationActive = true
        )

        val encoded = NavigationSessionStorage.encode(state, adapter)
        val decoded = NavigationSessionStorage.decode(encoded, adapter)

        assertThat(decoded).isNotNull()
        assertThat(decoded?.copy(latestResolution = SimonTurnResolution.None)).isEqualTo(
            state.copy(spokenPrompt = null, latestResolution = SimonTurnResolution.None)
        )
    }

    @Test
    fun `decode returns null for malformed state`() {
        assertThat(NavigationSessionStorage.decode("not-json", adapter)).isNull()
    }
}
