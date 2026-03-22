package com.simonsaysgps.domain.engine

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.PromptPersonality
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
        assertThat(
            factory.upcomingPrompt(
                maneuver = maneuver,
                unit = DistanceUnit.IMPERIAL,
                distanceMeters = 120.0,
                personality = PromptPersonality.CLASSIC_SIMON
            )
        ).contains("Simon says")
    }

    @Test
    fun `classic simon keeps the original unauthorized reroute line`() {
        assertThat(
            factory.reroutePrompt(RerouteReason.UNAUTHORIZED_TURN, PromptPersonality.CLASSIC_SIMON)
        ).isEqualTo("Oh, Simon didn't say. Rerouting.")
    }

    @Test
    fun `snarky simon authorized prompts still include simon says`() {
        assertThat(factory.immediatePrompt(maneuver, PromptPersonality.SNARKY_SIMON))
            .startsWith("Simon says")
    }

    @Test
    fun `polite simon uses courteous phrasing for authorized prompts`() {
        assertThat(factory.immediatePrompt(maneuver, PromptPersonality.POLITE_SIMON))
            .isEqualTo("Simon says please turn left onto pine street.")
    }

    @Test
    fun `all personalities keep unauthorized reroutes understandable`() {
        PromptPersonality.entries.forEach { personality ->
            assertThat(factory.reroutePrompt(RerouteReason.UNAUTHORIZED_TURN, personality))
                .contains("Rerouting")
        }
    }

    @Test
    fun `personalities produce distinct approval prompts`() {
        assertThat(factory.approvalPrompt(PromptPersonality.CLASSIC_SIMON))
            .isNotEqualTo(factory.approvalPrompt(PromptPersonality.SNARKY_SIMON))
        assertThat(factory.approvalPrompt(PromptPersonality.POLITE_SIMON))
            .isNotEqualTo(factory.approvalPrompt(PromptPersonality.CLASSIC_SIMON))
    }
}
