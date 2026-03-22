package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test

class RecentDestinationStorageTest {
    private val adapter = Moshi.Builder().build().adapter<List<StoredRecentDestination>>(
        Types.newParameterizedType(List::class.java, StoredRecentDestination::class.java)
    )

    @Test
    fun `encode and decode round trip recent destinations`() {
        val places = listOf(
            PlaceResult(
                id = "1",
                name = "Home",
                fullAddress = "1 Main St",
                coordinate = Coordinate(1.23, 4.56)
            ),
            PlaceResult(
                id = "2",
                name = "Office",
                fullAddress = "2 Market St",
                coordinate = Coordinate(7.89, 0.12)
            )
        )

        val encoded = RecentDestinationStorage.encode(places, adapter)
        val decoded = RecentDestinationStorage.decode(encoded, adapter)

        assertThat(decoded).isEqualTo(places)
    }

    @Test
    fun `decode gracefully handles missing or malformed values`() {
        assertThat(RecentDestinationStorage.decode(null, adapter)).isEmpty()
        assertThat(RecentDestinationStorage.decode("not-json", adapter)).isEmpty()
    }
}
