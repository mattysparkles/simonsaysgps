package com.simonsaysgps.ui.model

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.ArrivalStatus
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LaneDirection
import com.simonsaysgps.domain.model.LaneGuidance
import com.simonsaysgps.domain.model.LaneIndicator
import com.simonsaysgps.domain.model.LanePreference
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.TurnType
import org.junit.Test

class ActiveNavigationBannerUiMapperTest {
    @Test
    fun `arrived navigation maps to explicit arrival banner`() {
        val state = NavigationSessionState(
            route = route(),
            arrivalStatus = ArrivalStatus.ARRIVED,
            spokenPrompt = "You have arrived. Simon approves.",
            navigationActive = false
        )

        val banner = ActiveNavigationBannerUiMapper.map(state, SettingsModel())

        assertThat(banner).isInstanceOf(ActiveNavigationBannerUiModel.Arrived::class.java)
        banner as ActiveNavigationBannerUiModel.Arrived
        assertThat(banner.title).isEqualTo("You've arrived")
        assertThat(banner.message).contains("arrived")
    }

    @Test
    fun `info only maneuver maps clear authorization messaging and lane placeholder`() {
        val maneuver = maneuver(authorization = ManeuverAuthorization.NORMAL_INFO_ONLY)
        val state = NavigationSessionState(
            route = route(maneuvers = listOf(maneuver)),
            upcomingManeuver = maneuver,
            currentRoad = maneuver.roadName,
            distanceToNextManeuverMeters = 120.0,
            navigationActive = true
        )

        val banner = ActiveNavigationBannerUiMapper.map(state, SettingsModel())

        assertThat(banner).isInstanceOf(ActiveNavigationBannerUiModel.EnRoute::class.java)
        banner as ActiveNavigationBannerUiModel.EnRoute
        assertThat(banner.authorization.label).isEqualTo("INFO ONLY")
        assertThat(banner.authorization.message).contains("Preview only")
        assertThat(banner.laneGuidance).isInstanceOf(LaneGuidanceUiModel.Placeholder::class.java)
    }

    @Test
    fun `provider supplied lane guidance maps recommended lane emphasis`() {
        val laneGuidance = LaneGuidance(
            lanes = listOf(
                LaneIndicator(setOf(LaneDirection.LEFT), preference = LanePreference.NOT_RECOMMENDED),
                LaneIndicator(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), preference = LanePreference.RECOMMENDED)
            )
        )
        val maneuver = maneuver(laneGuidance = laneGuidance)
        val state = NavigationSessionState(
            route = route(maneuvers = listOf(maneuver)),
            upcomingManeuver = maneuver,
            currentRoad = maneuver.roadName,
            distanceToNextManeuverMeters = 55.0,
            navigationActive = true
        )

        val banner = ActiveNavigationBannerUiMapper.map(state, SettingsModel()) as ActiveNavigationBannerUiModel.EnRoute

        assertThat(banner.laneGuidance).isInstanceOf(LaneGuidanceUiModel.Ready::class.java)
        val laneModel = banner.laneGuidance as LaneGuidanceUiModel.Ready
        assertThat(laneModel.hint).isEqualTo("Use the highlighted lane.")
        assertThat(laneModel.lanes.any { it.emphasized && it.label.contains("↑ / →") }).isTrue()
    }

    private fun route(maneuvers: List<RouteManeuver> = listOf(maneuver())) = Route(
        geometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.0001)),
        maneuvers = maneuvers,
        totalDistanceMeters = 100.0,
        totalDurationSeconds = 90.0,
        etaEpochSeconds = 10L
    )

    private fun maneuver(
        authorization: ManeuverAuthorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
        laneGuidance: LaneGuidance? = null
    ) = RouteManeuver(
        id = "maneuver-1",
        coordinate = Coordinate(0.0, 0.0001),
        instruction = "Turn right onto Market Street",
        turnType = TurnType.RIGHT,
        roadName = "Market Street",
        distanceFromPreviousMeters = 30.0,
        distanceToNextMeters = 45.0,
        authorization = authorization,
        headingBefore = 0.0,
        headingAfter = 90.0,
        laneGuidance = laneGuidance
    )
}
