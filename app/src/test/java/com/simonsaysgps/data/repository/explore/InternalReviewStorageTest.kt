package com.simonsaysgps.data.repository.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.InternalReviewModerationStatus
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test

class InternalReviewStorageTest {
    private val adapter = Moshi.Builder().build().adapter<List<StoredInternalReview>>(
        Types.newParameterizedType(List::class.java, StoredInternalReview::class.java)
    )

    @Test
    fun `encode and decode round trip internal reviews`() {
        val reviews = listOf(
            InternalPlaceReview(
                internalReviewId = "review-1",
                canonicalPlaceId = "place-1",
                authorDisplayName = "Local driver",
                rating = 5,
                reviewText = "Quiet stop with quick service.",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L,
                tags = setOf(PlaceReviewTag.QUIET, PlaceReviewTag.DELICIOUS),
                moderationStatus = InternalReviewModerationStatus.VISIBLE
            )
        )

        val encoded = InternalReviewStorage.encode(reviews, adapter)
        val decoded = InternalReviewStorage.decode(encoded, adapter)

        assertThat(decoded).isEqualTo(reviews)
    }

    @Test
    fun `decode gracefully handles malformed values`() {
        assertThat(InternalReviewStorage.decode(null, adapter)).isEmpty()
        assertThat(InternalReviewStorage.decode("nope", adapter)).isEmpty()
    }
}
