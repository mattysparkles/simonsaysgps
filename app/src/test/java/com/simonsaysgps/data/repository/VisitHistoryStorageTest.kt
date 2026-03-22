package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.VisitHistoryEntry
import com.simonsaysgps.domain.model.VisitObservationSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test

class VisitHistoryStorageTest {
    private val adapter = Moshi.Builder().build().adapter<List<StoredVisitHistoryEntry>>(
        Types.newParameterizedType(List::class.java, StoredVisitHistoryEntry::class.java)
    )

    @Test
    fun `encode and decode round trip visit history`() {
        val visits = listOf(
            VisitHistoryEntry(
                id = "visit-1",
                placeId = "place-1",
                name = "Home",
                address = "1 Main St",
                coordinate = Coordinate(1.23, 4.56),
                visitedAtEpochMillis = 1_000L,
                confidence = 0.95f,
                source = VisitObservationSource.APP_CONFIRMED_ARRIVAL
            ),
            VisitHistoryEntry(
                id = "visit-2",
                placeId = "place-2",
                name = "Office",
                address = "2 Market St",
                coordinate = Coordinate(7.89, 0.12),
                visitedAtEpochMillis = 2_000L,
                confidence = 0.82f,
                source = VisitObservationSource.APP_CONFIRMED_SAVE
            )
        )

        val encoded = VisitHistoryStorage.encode(visits, adapter)
        val decoded = VisitHistoryStorage.decode(encoded, adapter)

        assertThat(decoded).isEqualTo(visits)
    }

    @Test
    fun `retention pruning and single-place deletion stay deterministic`() {
        val now = 1_000_000L
        val visits = listOf(
            VisitHistoryEntry("visit-1", "keep", "Keep", "1 Main St", Coordinate(1.0, 1.0), now - 10L, 0.9f, VisitObservationSource.APP_CONFIRMED_ARRIVAL),
            VisitHistoryEntry("visit-2", "drop", "Drop", "2 Main St", Coordinate(2.0, 2.0), now - 40L * 24L * 60L * 60L * 1000L, 0.9f, VisitObservationSource.APP_CONFIRMED_SAVE)
        )

        val retained = visits.prune(retentionDays = 30, nowEpochMillis = now)
        val removed = retained.filterNot { it.placeId == "keep" }

        assertThat(retained.map { it.placeId }).containsExactly("keep")
        assertThat(removed).isEmpty()
    }

    @Test
    fun `decode gracefully handles missing or malformed values`() {
        assertThat(VisitHistoryStorage.decode(null, adapter)).isEmpty()
        assertThat(VisitHistoryStorage.decode("not-json", adapter)).isEmpty()
    }
}
