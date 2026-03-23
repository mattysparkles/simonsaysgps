package com.simonsaysgps.domain.model.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import org.junit.Test

class ExploreCanonicalPlaceIdFactoryTest {
    @Test
    fun `canonical place id stays stable across provider link order`() {
        val left = ExploreCandidate(
            id = "a",
            name = "Scenic Cafe",
            typeLabel = "Cafe",
            address = "10 Main Street",
            coordinate = Coordinate(40.0, -73.0),
            facets = emptySet(),
            providerLinks = listOf(ExploreProviderLink("b", "2"), ExploreProviderLink("a", "1"))
        )
        val right = left.copy(id = "b", providerLinks = left.providerLinks.reversed())

        assertThat(ExploreCanonicalPlaceIdFactory.fromCandidate(left))
            .isEqualTo(ExploreCanonicalPlaceIdFactory.fromCandidate(right))
    }

    @Test
    fun `canonical place id favors normalized venue data over provider specific ids`() {
        val left = ExploreCandidate(
            id = "a",
            name = "Corner Market",
            typeLabel = "Market",
            address = "25 Broadway Ave",
            coordinate = Coordinate(40.00001, -73.00001),
            facets = emptySet(),
            providerLinks = listOf(ExploreProviderLink("provider-a", "123"))
        )
        val right = left.copy(id = "b", providerLinks = listOf(ExploreProviderLink("provider-b", "xyz")))

        assertThat(ExploreCanonicalPlaceIdFactory.fromCandidate(left))
            .isEqualTo(ExploreCanonicalPlaceIdFactory.fromCandidate(right))
    }
}
