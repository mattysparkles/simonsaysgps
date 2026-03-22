package com.simonsaysgps.domain.service.explore

import com.simonsaysgps.domain.model.explore.AccessiblePlacesPreference
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreCategoryHeuristics
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReason
import com.simonsaysgps.domain.model.explore.ExploreResult
import com.simonsaysgps.domain.model.explore.QuietPreferenceStrictness
import com.simonsaysgps.domain.util.GeoUtils
import kotlin.math.max

class ExploreRankingEngine {
    fun rank(query: ExploreQuery, candidates: List<ExploreCandidate>): List<ExploreResult> {
        return candidates
            .filter { passesHardFilters(query, it) }
            .map { candidate ->
                val distanceMeters = GeoUtils.distanceMeters(query.userLocation, candidate.coordinate)
                val offRouteDistanceMeters = query.activeRoute?.geometry?.takeIf { it.size >= 2 }?.let {
                    GeoUtils.closestDistanceToPolylineMeters(candidate.coordinate, it)
                }
                val breakdown = linkedMapOf<String, Double>()
                breakdown["distance"] = distanceScore(distanceMeters, query)
                breakdown["openNow"] = openNowScore(candidate, query)
                breakdown["reviews"] = reviewsScore(candidate, query)
                breakdown["category"] = ExploreCategoryHeuristics.relevance(query.category, candidate)
                breakdown["eventTiming"] = eventTimingScore(candidate, query)
                breakdown["novelty"] = noveltyScore(candidate)
                breakdown["routeAlignment"] = routeAlignmentScore(query, offRouteDistanceMeters)
                breakdown["homeProximity"] = homeProximityScore(query, candidate)
                breakdown["surprise"] = surpriseScore(candidate, query)
                breakdown["quiet"] = quietPreferenceScore(candidate, query)
                breakdown["accessibility"] = accessibilityScore(candidate, query)
                breakdown["promotions"] = promotionScore(candidate)
                breakdown["ratingConfidence"] = ratingConfidenceScore(candidate)

                val weightedScore =
                    breakdown["distance"]!! * 0.15 +
                        breakdown["openNow"]!! * 0.12 +
                        breakdown["reviews"]!! * 0.16 +
                        breakdown["category"]!! * 0.18 +
                        breakdown["eventTiming"]!! * 0.1 +
                        breakdown["novelty"]!! * 0.06 +
                        breakdown["routeAlignment"]!! * 0.07 +
                        breakdown["homeProximity"]!! * 0.04 +
                        breakdown["surprise"]!! * 0.02 +
                        breakdown["quiet"]!! * 0.03 +
                        breakdown["accessibility"]!! * 0.03 +
                        breakdown["promotions"]!! * 0.02 +
                        breakdown["ratingConfidence"]!! * 0.02

                ExploreResult(
                    candidate = candidate,
                    score = weightedScore,
                    confidence = confidence(candidate, breakdown),
                    distanceMeters = distanceMeters,
                    offRouteDistanceMeters = offRouteDistanceMeters,
                    reasons = explain(query.category, candidate, breakdown, distanceMeters, offRouteDistanceMeters),
                    debugBreakdown = breakdown
                )
            }
            .sortedByDescending { it.score }
    }

    private fun passesHardFilters(query: ExploreQuery, candidate: ExploreCandidate): Boolean {
        val settings = query.settings
        if (settings.requireOpenNowByDefault && query.category != ExploreCategory.NEW && candidate.primaryEvent == null && candidate.openNow == false) return false
        if (query.category == ExploreCategory.NEVER_BEEN && candidate.visitedBefore) return false
        if (settings.kidFriendlyOnly && candidate.kidFriendly == false) return false
        if (settings.avoidAlcoholFocusedVenues && candidate.alcoholFocused) return false
        if (settings.avoidAdultOrientedVenues && candidate.adultOriented) return false
        if (settings.accessiblePlacesPreference == AccessiblePlacesPreference.ACCESSIBLE_ONLY && candidate.accessible == false) return false
        return true
    }

    private fun distanceScore(distanceMeters: Double, query: ExploreQuery): Double {
        val radiusMeters = query.settings.defaultRadiusMiles * METERS_PER_MILE
        return (1.0 - (distanceMeters / max(radiusMeters.toDouble(), 1.0))).coerceIn(0.0, 1.0)
    }

    private fun openNowScore(candidate: ExploreCandidate, query: ExploreQuery): Double = when {
        candidate.primaryEvent != null && eventTimingScore(candidate, query) >= 0.9 -> 0.95
        candidate.openNow == true -> if (query.category == ExploreCategory.DELICIOUS || query.category == ExploreCategory.OPEN_NOW) 1.0 else 0.8
        candidate.openNow == false -> if (query.category == ExploreCategory.OPEN_NOW) 0.05 else 0.25
        else -> 0.45
    }

    private fun reviewsScore(candidate: ExploreCandidate, query: ExploreQuery): Double {
        val reviews = candidate.reviewSummary ?: return 0.45
        val external = (reviews.averageRating / 5.0).coerceIn(0.0, 1.0)
        val internal = reviews.internalAverageRating?.div(5.0)?.coerceIn(0.0, 1.0) ?: external
        val countConfidence = (reviews.totalCount.coerceAtMost(500) / 500.0).coerceIn(0.05, 1.0)
        val blended = if (query.settings.useInternalReviewsFirst) {
            (internal * 0.7) + (external * 0.3)
        } else {
            (external * 0.7) + (internal * 0.3)
        }
        return (blended * 0.8 + countConfidence * 0.2).coerceIn(0.0, 1.0)
    }

    private fun ratingConfidenceScore(candidate: ExploreCandidate): Double {
        val reviewSummary = candidate.reviewSummary ?: return 0.2
        val countScore = (reviewSummary.totalCount.coerceAtMost(400) / 400.0).coerceIn(0.0, 1.0)
        val internalBoost = if (reviewSummary.internalCount > 0) 0.15 else 0.0
        return (countScore + internalBoost).coerceIn(0.0, 1.0)
    }

    private fun eventTimingScore(candidate: ExploreCandidate, query: ExploreQuery): Double {
        val event = candidate.primaryEvent ?: return 0.2
        val millisUntilStart = event.startEpochMillis - query.nowEpochMillis
        return when {
            millisUntilStart <= 0L -> 1.0
            millisUntilStart <= ONE_HOUR_MS -> 0.9
            millisUntilStart <= THREE_HOURS_MS -> 0.7
            millisUntilStart <= TWELVE_HOURS_MS -> 0.4
            else -> 0.2
        }
    }

    private fun promotionScore(candidate: ExploreCandidate): Double {
        val promotion = candidate.primaryPromotion ?: return 0.2
        return if (promotion.inferred) promotion.confidence * 0.75 else promotion.confidence.toDouble()
    }

    private fun noveltyScore(candidate: ExploreCandidate): Double {
        val newConfidence = candidate.confidenceSignals.firstOrNull { it.label.equals("new", ignoreCase = true) }?.confidence?.toDouble() ?: 0.0
        return when {
            candidate.visitedBefore -> 0.15
            candidate.recentlyOpenedOrTrending -> max(1.0, newConfidence)
            newConfidence > 0.0 -> max(0.65, newConfidence)
            else -> 0.75
        }.coerceIn(0.0, 1.0)
    }

    private fun routeAlignmentScore(query: ExploreQuery, offRouteDistanceMeters: Double?): Double {
        if (query.category != ExploreCategory.ON_MY_WAY) return 0.4
        if (query.activeRoute == null || !query.settings.allowRouteDetoursWhileNavigating) return 0.1
        val maxDetourMeters = query.settings.maxDetourDistanceMiles * METERS_PER_MILE
        val distance = offRouteDistanceMeters ?: return 0.2
        return (1.0 - (distance / max(maxDetourMeters, 1.0))).coerceIn(0.0, 1.0)
    }

    private fun homeProximityScore(query: ExploreQuery, candidate: ExploreCandidate): Double {
        if (query.category != ExploreCategory.CLOSE_TO_HOME) return 0.35
        val home = query.settings.homeCoordinate ?: return 0.1
        val homeDistance = GeoUtils.distanceMeters(home, candidate.coordinate)
        return (1.0 - (homeDistance / (query.settings.defaultRadiusMiles * METERS_PER_MILE))).coerceIn(0.0, 1.0)
    }

    private fun surpriseScore(candidate: ExploreCandidate, query: ExploreQuery): Double {
        val entropyBoost = when {
            ExploreFacet.TRENDING in candidate.facets || ExploreFacet.NEW in candidate.facets -> 0.9
            ExploreFacet.SCENIC in candidate.facets || ExploreFacet.ACTIVITY in candidate.facets -> 0.75
            else -> 0.4
        }
        return (entropyBoost.toFloat() * query.settings.surpriseMeWeight).coerceIn(0.0f, 1.0f).toDouble()
    }

    private fun quietPreferenceScore(candidate: ExploreCandidate, query: ExploreQuery): Double {
        val quiet = candidate.quietScore?.toDouble() ?: 0.45
        return when (query.settings.quietPreferenceStrictness) {
            QuietPreferenceStrictness.RELAXED -> max(quiet, 0.35)
            QuietPreferenceStrictness.BALANCED -> quiet
            QuietPreferenceStrictness.STRICT -> if (ExploreFacet.QUIET in candidate.facets) max(quiet, 0.8) else quiet * 0.6
        }
    }

    private fun accessibilityScore(candidate: ExploreCandidate, query: ExploreQuery): Double = when (query.settings.accessiblePlacesPreference) {
        AccessiblePlacesPreference.FLEXIBLE -> 0.5
        AccessiblePlacesPreference.PREFER_ACCESSIBLE -> if (candidate.accessible == true) 1.0 else 0.35
        AccessiblePlacesPreference.ACCESSIBLE_ONLY -> if (candidate.accessible == true) 1.0 else 0.0
    }

    private fun confidence(candidate: ExploreCandidate, breakdown: Map<String, Double>): Float {
        val signalCount = listOf(
            candidate.openNow,
            candidate.reviewSummary,
            candidate.primaryEvent,
            candidate.primaryPromotion,
            candidate.quietScore,
            candidate.accessible,
            candidate.phoneNumber,
            candidate.websiteUrl
        ).count { it != null }
        val confidenceSignals = candidate.confidenceSignals.map { if (it.inferred) it.confidence * 0.75f else it.confidence }
        val confidenceSignalAverage = confidenceSignals.average().takeIf { !it.isNaN() }?.toFloat() ?: 0.7f
        val computed = (candidate.sourceConfidence + (signalCount * 0.03f) + confidenceSignalAverage * 0.08f + (breakdown.values.average().toFloat() * 0.14f)).coerceIn(0.0f, 1.0f)
        return computed
    }

    private fun explain(
        category: ExploreCategory,
        candidate: ExploreCandidate,
        breakdown: Map<String, Double>,
        distanceMeters: Double,
        offRouteDistanceMeters: Double?
    ): List<ExploreReason> {
        val reasons = mutableListOf<ExploreReason>()
        reasons += ExploreReason(
            title = "Category match",
            detail = "Strong match for ${category.displayName.lowercase()} because ${candidate.typeLabel.lowercase()} aligns with the selected Explore intent.",
            contribution = breakdown["category"] ?: 0.0,
            confidence = candidate.sourceConfidence
        )
        if (candidate.openNow == true) {
            reasons += ExploreReason("Open now", "Open right now, which helps this suggestion stay practical.", breakdown["openNow"] ?: 0.0, 0.9f)
        }
        candidate.reviewSummary?.let { summary ->
            val providerLabels = summary.sources.joinToString { it.providerLabel }
            reasons += ExploreReason(
                title = "Reviews",
                detail = "Review signal is solid at ${"%.1f".format(summary.averageRating)}★ across ${summary.totalCount} ratings${providerLabels.takeIf { it.isNotBlank() }?.let { " from $it" } ?: ""}.",
                contribution = breakdown["reviews"] ?: 0.0,
                confidence = 0.8f
            )
        }
        if (distanceMeters > 0) {
            reasons += ExploreReason(
                title = "Distance",
                detail = "Only ${distanceMiles(distanceMeters)} miles away from your current location.",
                contribution = breakdown["distance"] ?: 0.0,
                confidence = 0.95f
            )
        }
        candidate.primaryEvent?.let { event ->
            reasons += ExploreReason(
                title = "Timing",
                detail = "Event timing helps: ${event.title} is ${event.timing.name.lowercase().replace('_', ' ')}.",
                contribution = breakdown["eventTiming"] ?: 0.0,
                confidence = event.confidence
            )
        }
        candidate.primaryPromotion?.let { promo ->
            reasons += ExploreReason(
                title = if (promo.inferred) "Possible deal" else "Deal",
                detail = "${if (promo.inferred) "Inferred" else "Provider-backed"} promotion signal: ${promo.summary}",
                contribution = breakdown["promotions"] ?: 0.0,
                confidence = promo.confidence
            )
        }
        if (offRouteDistanceMeters != null) {
            reasons += ExploreReason(
                title = "Route fit",
                detail = "About ${distanceMiles(offRouteDistanceMeters)} miles off your active route.",
                contribution = breakdown["routeAlignment"] ?: 0.0,
                confidence = 0.85f
            )
        }
        candidate.whyChosenHints.forEach { hint ->
            reasons += ExploreReason("Why this was chosen", hint, 0.5, candidate.sourceConfidence)
        }
        return reasons.sortedByDescending { it.contribution }
    }

    private fun distanceMiles(distanceMeters: Double): String = "%.1f".format(distanceMeters / METERS_PER_MILE)

    private companion object {
        const val METERS_PER_MILE = 1609.34
        const val ONE_HOUR_MS = 60 * 60 * 1000L
        const val THREE_HOURS_MS = 3 * ONE_HOUR_MS
        const val TWELVE_HOURS_MS = 12 * ONE_HOUR_MS
    }
}
