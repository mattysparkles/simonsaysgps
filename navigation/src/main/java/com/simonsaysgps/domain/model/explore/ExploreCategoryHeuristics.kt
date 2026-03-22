package com.simonsaysgps.domain.model.explore

object ExploreCategoryHeuristics {
    fun relevance(category: ExploreCategory, candidate: ExploreCandidate): Double {
        val facets = candidate.facets
        return when (category) {
            ExploreCategory.DELICIOUS -> weightedMatch(candidate, ExploreFacet.FOOD to 0.55, ExploreFacet.DRINK to 0.25, ExploreFacet.SALE to 0.05)
            ExploreCategory.FUN -> weightedMatch(candidate, ExploreFacet.ENTERTAINMENT to 0.45, ExploreFacet.ACTIVITY to 0.25, ExploreFacet.PARK to 0.15, ExploreFacet.KIDS to 0.15)
            ExploreCategory.OPEN_NOW -> if (candidate.openNow == true) 1.0 else 0.35
            ExploreCategory.NEVER_BEEN -> if (candidate.visitedBefore) 0.0 else 1.0
            ExploreCategory.QUIET -> ((candidate.quietScore ?: 0.45f).toDouble() * 0.6) + weightedMatch(candidate, ExploreFacet.QUIET to 0.2, ExploreFacet.NATURE to 0.1, ExploreFacet.LEARNING to 0.1)
            ExploreCategory.OUTDOORS -> weightedMatch(candidate, ExploreFacet.OUTDOORS to 0.45, ExploreFacet.PARK to 0.3, ExploreFacet.NATURE to 0.15, ExploreFacet.SCENIC to 0.1)
            ExploreCategory.IMPORTANT -> weightedMatch(candidate, ExploreFacet.IMPORTANT to 0.25, ExploreFacet.HISTORY to 0.25, ExploreFacet.GOVERNMENT to 0.2, ExploreFacet.LANDMARK to 0.2, ExploreFacet.LEARNING to 0.1)
            ExploreCategory.CLOSE_TO_HOME -> weightedMatch(candidate, ExploreFacet.FOOD to 0.1, ExploreFacet.SHOPPING to 0.1, ExploreFacet.PARK to 0.1, ExploreFacet.LANDMARK to 0.1) + 0.6
            ExploreCategory.ON_MY_WAY -> weightedMatch(candidate, ExploreFacet.FOOD to 0.15, ExploreFacet.SHOPPING to 0.1, ExploreFacet.ENTERTAINMENT to 0.1, ExploreFacet.IMPORTANT to 0.05) + 0.6
            ExploreCategory.SPECIAL -> weightedMatch(candidate, ExploreFacet.SCENIC to 0.3, ExploreFacet.LANDMARK to 0.25, ExploreFacet.HISTORY to 0.15, ExploreFacet.TRENDING to 0.15, ExploreFacet.IMPORTANT to 0.15)
            ExploreCategory.NEW -> if (candidate.recentlyOpenedOrTrending || ExploreFacet.NEW in facets || ExploreFacet.TRENDING in facets) 1.0 else 0.25
            ExploreCategory.I_CAN_SHOP -> weightedMatch(candidate, ExploreFacet.SHOPPING to 0.7, ExploreFacet.SALE to 0.2, ExploreFacet.TRENDING to 0.1)
            ExploreCategory.I_CAN_LEARN -> weightedMatch(candidate, ExploreFacet.LEARNING to 0.5, ExploreFacet.HISTORY to 0.2, ExploreFacet.IMPORTANT to 0.15, ExploreFacet.LANDMARK to 0.15)
            ExploreCategory.GOOD_FOR_KIDS -> weightedMatch(candidate, ExploreFacet.KIDS to 0.55, ExploreFacet.ACTIVITY to 0.2, ExploreFacet.PARK to 0.15, ExploreFacet.ENTERTAINMENT to 0.1)
            ExploreCategory.HAVING_A_SALE -> if (candidate.promotionInfo != null || ExploreFacet.SALE in facets) 1.0 else 0.2
        }.coerceIn(0.0, 1.0)
    }

    private fun weightedMatch(candidate: ExploreCandidate, vararg weights: Pair<ExploreFacet, Double>): Double {
        return weights.sumOf { (facet, weight) -> if (facet in candidate.facets) weight else 0.0 }.coerceIn(0.0, 1.0)
    }
}
