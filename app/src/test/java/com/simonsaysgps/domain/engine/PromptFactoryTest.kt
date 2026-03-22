package com.simonsaysgps.domain.engine

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.service.PromptFactory
import org.junit.Test

class PromptFactoryTest {
    private val factory = PromptFactory()
    private val maneuver = RouteManeuver(
        id = "1",
        coordinate = Coordinate(1.0, 1.0),
        instruction = "Turn left onto Pine Street",
        turnType = TurnType.LEFT,
        roadName = "Pine Street",
        distanceFromPreviousMeters = 50.0,
        distanceToNextMeters = 50.0,
        authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
        headingBefore = 0.0,
        headingAfter = -90.0
    )

    @Test
    fun `prompt prepends simon says for required maneuvers`() {
        assertThat(factory.upcomingPrompt(maneuver, DistanceUnit.IMPERIAL, 120.0)).contains("Simon says")
    }

    @Test
    fun `reroute prompt is playful for unauthorized turns`() {
        assertThat(factory.reroutePrompt(RerouteReason.UNAUTHORIZED_TURN)).isEqualTo("Oh, Simon didn't say. Rerouting.")
    }
}
