package com.simonsaysgps.config

import com.simonsaysgps.BuildConfig
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel

data class ReleaseSurface(
    val releaseSafeSurface: Boolean,
    val graphHopperConfigured: Boolean
) {
    val showDeveloperOptions: Boolean = !releaseSafeSurface
    val showDebugOverlayControls: Boolean = !releaseSafeSurface
    val showExperimentalRoutingProviders: Boolean = !releaseSafeSurface
    val showProviderDiagnostics: Boolean = !releaseSafeSurface
    val showHeavyVehicleDimensions: Boolean = !releaseSafeSurface
    val showSoundtrackScaffolding: Boolean = !releaseSafeSurface
    val showReviewModerationHooks: Boolean = !releaseSafeSurface
    val showLaneGuidancePlaceholder: Boolean = !releaseSafeSurface

    fun availableRoutingProviders(): List<RoutingProvider> = buildList {
        add(RoutingProvider.OSRM)
        if (graphHopperConfigured || !releaseSafeSurface) add(RoutingProvider.GRAPH_HOPPER)
        if (showExperimentalRoutingProviders) add(RoutingProvider.VALHALLA)
    }

    fun sanitize(settings: SettingsModel): SettingsModel {
        val availableProviders = availableRoutingProviders().toSet()
        return settings.copy(
            routingProvider = settings.routingProvider.takeIf { it in availableProviders } ?: RoutingProvider.OSRM,
            debugMode = settings.debugMode && showDebugOverlayControls,
            demoMode = settings.demoMode && showDeveloperOptions,
            voiceAssistantSettings = settings.voiceAssistantSettings.copy(
                soundtrackIntegrationEnabled = settings.voiceAssistantSettings.soundtrackIntegrationEnabled && showSoundtrackScaffolding
            )
        )
    }

    companion object {
        fun fromBuildConfig(): ReleaseSurface = ReleaseSurface(
            releaseSafeSurface = BuildConfig.RELEASE_SAFE_SURFACE,
            graphHopperConfigured = BuildConfig.GRAPH_HOPPER_API_KEY.isNotBlank()
        )
    }
}
