package com.simonsaysgps.domain.service

import com.simonsaysgps.domain.model.RouteStyle
import com.simonsaysgps.domain.model.RoutingPreferences
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.TransportProfile

enum class SupportLevel { FULL, PARTIAL, UNSUPPORTED }

data class ProviderRoutingPlan(
    val providerProfileHint: String,
    val requestedStyles: List<String>,
    val advisory: RoutingSupportAdvisory
)

data class RoutingSupportAdvisory(
    val summary: String,
    val limitations: List<String>,
    val safetyCriticalSupport: SupportLevel,
    val profileAwareRoutingReady: Boolean
)

object RoutingSupportAdvisor {
    fun plan(settings: SettingsModel): ProviderRoutingPlan {
        val preferences = settings.routingPreferences
        val advisory = advisory(settings.routingProvider, preferences)
        return ProviderRoutingPlan(
            providerProfileHint = providerProfileHint(settings.routingProvider, preferences),
            requestedStyles = requestedStyles(preferences),
            advisory = advisory
        )
    }

    fun advisory(provider: RoutingProvider, preferences: RoutingPreferences): RoutingSupportAdvisory {
        val limitations = mutableListOf<String>()
        val heavyVehicleProfile = preferences.transportProfile in setOf(
            TransportProfile.RV,
            TransportProfile.TRUCK_COMMERCIAL,
            TransportProfile.TRAILER_TOWING
        )
        val micromobilityProfile = preferences.transportProfile in setOf(TransportProfile.E_BIKE, TransportProfile.E_SKATEBOARD)
        val safetyCriticalRequested = heavyVehicleProfile || preferences.vehicleProfile.hasSafetyCriticalRestrictions()

        if (safetyCriticalRequested) {
            limitations += "${provider.displayName} in this build does not guarantee enforcement of truck, RV, trailer, height, length, or weight restrictions. Always verify the route yourself."
        }
        if (preferences.preferLowStress || preferences.primaryRouteStyle == RouteStyle.LOW_STRESS) {
            limitations += "Low-stress routing is a preference scaffold right now. The current provider may not expose a dedicated low-stress network model."
        }
        if (preferences.avoidTolls || preferences.primaryRouteStyle == RouteStyle.NO_TOLLS) {
            limitations += "Toll avoidance is requested as a preference, but the current provider path in this build does not guarantee toll exclusion on every route."
        }
        if (preferences.preferScenic || preferences.primaryRouteStyle == RouteStyle.SCENIC) {
            limitations += "Scenic routing is bounded to a preference layer in this build and is not a guaranteed scenic-road optimizer yet."
        }
        if (preferences.simonChallengeMode || preferences.primaryRouteStyle == RouteStyle.SIMON_CHALLENGE) {
            limitations += "Simon Challenge Mode stays bounded and never intentionally creates absurd loops; in this build it is a preference layer, not a guaranteed route reshaper."
        }
        if (micromobilityProfile) {
            limitations += "Micromobility profiles currently map onto the closest supported provider profile instead of a fully dedicated network model."
        }

        val summary = when {
            safetyCriticalRequested -> "Profile-aware routing is scaffolded, but safety-critical vehicle restrictions are only partially supported today."
            limitations.isEmpty() -> "Current provider matches the selected transport profile without extra caveats."
            else -> "Current provider can use your transport preferences as hints, with a few honest limitations shown below."
        }
        return RoutingSupportAdvisory(
            summary = summary,
            limitations = limitations,
            safetyCriticalSupport = when {
                safetyCriticalRequested -> SupportLevel.PARTIAL
                else -> SupportLevel.PARTIAL
            },
            profileAwareRoutingReady = true
        )
    }

    private fun providerProfileHint(provider: RoutingProvider, preferences: RoutingPreferences): String {
        val profile = preferences.transportProfile
        return when (provider) {
            RoutingProvider.GRAPH_HOPPER -> when (profile) {
                TransportProfile.WALKING -> "foot"
                TransportProfile.BICYCLE, TransportProfile.E_BIKE -> "bike"
                else -> "car"
            }
            else -> when (profile) {
                TransportProfile.WALKING -> "walking-hint"
                TransportProfile.BICYCLE, TransportProfile.E_BIKE -> "cycling-hint"
                else -> "driving-hint"
            }
        }
    }

    private fun requestedStyles(preferences: RoutingPreferences): List<String> {
        return buildList {
            add(preferences.primaryRouteStyle.displayName)
            if (preferences.avoidTolls && preferences.primaryRouteStyle != RouteStyle.NO_TOLLS) add(RouteStyle.NO_TOLLS.displayName)
            if (preferences.preferScenic && preferences.primaryRouteStyle != RouteStyle.SCENIC) add(RouteStyle.SCENIC.displayName)
            if (preferences.preferLowStress && preferences.primaryRouteStyle != RouteStyle.LOW_STRESS) add(RouteStyle.LOW_STRESS.displayName)
            if (preferences.simonChallengeMode && preferences.primaryRouteStyle != RouteStyle.SIMON_CHALLENGE) add(RouteStyle.SIMON_CHALLENGE.displayName)
        }
    }
}
