package com.simonsaysgps.domain.engine

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.HeadingConfidence
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.TurnType
import org.junit.Test

class TurnDetectorTest {
    private val detector = TurnDetector()
    private val maneuver = RouteManeuver(
        id = "m1",
        coordinate = Coordinate(0.0, 0.0001),
        instruction = "Turn right",
        turnType = TurnType.RIGHT,
        roadName = null,
        distanceFromPreviousMeters = 20.0,
        distanceToNextMeters = 20.0,
        authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
        headingBefore = 0.0,
        headingAfter = 90.0
    )

    @Test
    fun `detector recognizes maneuver turn near step`() {
        val detection = detector.detect(
            previous = sample(0.0, 0.0, 0f),
            current = sample(0.0, 0.0001, 90f),
            maneuver = maneuver,
            routeGeometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.0001))
        )
        assertThat(detection.occurred).isTrue()
        assertThat(detection.onRouteCorridor).isTrue()
        assertThat(detection.headingConfidence).isEqualTo(HeadingConfidence.HIGH)
    }

    @Test
    fun `detector rejects slight heading drift while slowly approaching turn`() {
        val detection = detector.detect(
            previous = sample(0.0, 0.00006, 5f, speed = 1f, timestampMillis = 1_000L),
            current = sample(0.0, 0.00008, 22f, speed = 1f, timestampMillis = 2_000L),
            maneuver = maneuver,
            routeGeometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.0001), Coordinate(0.0001, 0.0001))
        )

        assertThat(detection.headingConfidence).isEqualTo(HeadingConfidence.LOW)
        assertThat(detection.matchedExpectedTurn).isFalse()
        assertThat(detection.occurred).isFalse()
    }

    private fun sample(
        lat: Double,
        lon: Double,
        bearing: Float,
        speed: Float = 5f,
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
