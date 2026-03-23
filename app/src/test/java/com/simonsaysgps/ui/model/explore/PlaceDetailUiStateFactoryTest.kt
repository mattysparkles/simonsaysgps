package com.simonsaysgps.ui.model.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExternalReviewSummaryBlock
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.InternalReviewAggregate
import com.simonsaysgps.domain.model.explore.PlaceDetailRecord
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import org.junit.Test

class PlaceDetailUiStateFactoryTest {
    @Test
    fun `ui state reports partial data when optional metadata is missing`() {
        val state = PlaceDetailUiStateFactory.fromRecord(record())

        assertThat(state.status).isEqualTo(PlaceDetailStatus.PARTIAL)
        assertThat(state.internalRatingSummary).contains("internal review")
        assertThat(state.externalRatingSummary).contains("Provider")
    }

    @Test
    fun `review compose validation rejects missing rating and short text`() {
        assertThat(ReviewComposeValidator.validate(0, "good")).contains("Choose a rating")
        assertThat(ReviewComposeValidator.validate(4, "short")).contains("at least 10 characters")
        assertThat(ReviewComposeValidator.validate(4, "quiet stop with easy parking")).isNull()
    }

    @Test
    fun `loading error and empty states are explicit`() {
        assertThat(PlaceDetailUiStateFactory.loading("place-1").status).isEqualTo(PlaceDetailStatus.LOADING)
        assertThat(PlaceDetailUiStateFactory.error("place-1", "boom").status).isEqualTo(PlaceDetailStatus.ERROR)
        assertThat(PlaceDetailUiStateFactory.empty("none").status).isEqualTo(PlaceDetailStatus.EMPTY)
    }

    private fun record() = PlaceDetailRecord(
        canonicalPlaceId = "place-1",
        name = "Cafe",
        typeLabel = "Cafe",
        address = "1 Main Street",
        coordinate = Coordinate(1.0, 2.0),
        openNow = true,
        distanceMeters = 321.0,
        whyChosen = "Close and reliable",
        placeTags = listOf("Food"),
        sourceAttributions = listOf(ExploreSourceAttribution("provider", "Provider", verified = true)),
        internalAggregate = InternalReviewAggregate(4.5, 2, listOf(PlaceReviewTag.QUIET)),
        internalReviews = listOf(
            InternalPlaceReview("review-1", "place-1", "Local driver", 5, "Great stop for families.", 1L, 2L)
        ),
        externalReviewSummaries = listOf(
            ExternalReviewSummaryBlock("provider", "Provider", 4.3, 20, "Summary", ExploreSourceAttribution("provider", "Provider", verified = false), 0.8f)
        )
    )
}
