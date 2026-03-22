package com.simonsaysgps.domain.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreCategoryHeuristics
import com.simonsaysgps.domain.model.explore.ExploreFacet
import org.junit.Test

class ExploreCategoryHeuristicsTest {
    @Test
    fun `good for kids favors kid friendly activity facets`() {
        val arcade = candidate(setOf(ExploreFacet.KIDS, ExploreFacet.ACTIVITY, ExploreFacet.ENTERTAINMENT))
        val governmentOffice = candidate(setOf(ExploreFacet.GOVERNMENT, ExploreFacet.IMPORTANT))

        assertThat(ExploreCategoryHeuristics.relevance(ExploreCategory.GOOD_FOR_KIDS, arcade))
            .isGreaterThan(ExploreCategoryHeuristics.relevance(ExploreCategory.GOOD_FOR_KIDS, governmentOffice))
    }

    @Test
    fun `new category degrades gracefully without perfect freshness data`() {
        val neutral = candidate(setOf(ExploreFacet.SHOPPING))
        val trending = candidate(setOf(ExploreFacet.SHOPPING, ExploreFacet.TRENDING), recentlyOpenedOrTrending = true)

        assertThat(ExploreCategoryHeuristics.relevance(ExploreCategory.NEW, trending)).isEqualTo(1.0)
        assertThat(ExploreCategoryHeuristics.relevance(ExploreCategory.NEW, neutral)).isGreaterThan(0.0)
    }

    private fun candidate(
        facets: Set<ExploreFacet>,
        recentlyOpenedOrTrending: Boolean = false
    ) = ExploreCandidate(
        id = "id",
        name = "Candidate",
        typeLabel = "Test",
        address = "123 Test St",
        coordinate = Coordinate(0.0, 0.0),
        facets = facets,
        recentlyOpenedOrTrending = recentlyOpenedOrTrending
    )
}
