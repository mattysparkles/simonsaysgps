package com.simonsaysgps.data.repository.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.model.explore.SavedPlaceRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test

class SavedPlaceStorageTest {
    private val adapter = Moshi.Builder().build().adapter<List<StoredSavedPlaceRecord>>(
        Types.newParameterizedType(List::class.java, StoredSavedPlaceRecord::class.java)
    )

    @Test
    fun `encode and decode round trip saved places`() {
        val places = listOf(
            SavedPlaceRecord(
                canonicalPlaceId = "place-1",
                name = "Scenic Cafe",
                typeLabel = "Cafe",
                address = "1 Main Street",
                coordinate = Coordinate(40.0, -73.0),
                sourceAttributions = listOf(ExploreSourceAttribution("provider", "Provider", verified = true)),
                savedAtEpochMillis = 123L
            )
        )

        val encoded = SavedPlaceStorage.encode(places, adapter)
        val decoded = SavedPlaceStorage.decode(encoded, adapter)

        assertThat(decoded).isEqualTo(places)
    }

    @Test
    fun `decode gracefully handles invalid data`() {
        assertThat(SavedPlaceStorage.decode("not-json", adapter)).isEmpty()
    }
}
