package com.simonsaysgps.config

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import org.junit.Test

class ReleaseSurfaceTest {
    @Test
    fun `release surface hides unfinished provider and developer scaffolding`() {
        val releaseSurface = ReleaseSurface(releaseSafeSurface = true, graphHopperConfigured = false)

        assertThat(releaseSurface.availableRoutingProviders()).containsExactly(RoutingProvider.OSRM)
        assertThat(releaseSurface.showProviderDiagnostics).isFalse()
        assertThat(releaseSurface.showHeavyVehicleDimensions).isFalse()
        assertThat(releaseSurface.showSoundtrackScaffolding).isFalse()
        assertThat(releaseSurface.showReviewModerationHooks).isFalse()
        assertThat(releaseSurface.showLaneGuidancePlaceholder).isFalse()
    }

    @Test
    fun `sanitize resets unsupported release settings to safe defaults`() {
        val releaseSurface = ReleaseSurface(releaseSafeSurface = true, graphHopperConfigured = false)
        val unsafe = SettingsModel(
            routingProvider = RoutingProvider.VALHALLA,
            debugMode = true,
            demoMode = true,
            voiceAssistantSettings = SettingsModel().voiceAssistantSettings.copy(soundtrackIntegrationEnabled = true)
        )

        val sanitized = releaseSurface.sanitize(unsafe)

        assertThat(sanitized.routingProvider).isEqualTo(RoutingProvider.OSRM)
        assertThat(sanitized.debugMode).isFalse()
        assertThat(sanitized.demoMode).isFalse()
        assertThat(sanitized.voiceAssistantSettings.soundtrackIntegrationEnabled).isFalse()
    }

    @Test
    fun `developer surface keeps configured experimental controls visible`() {
        val debugSurface = ReleaseSurface(releaseSafeSurface = false, graphHopperConfigured = false)

        assertThat(debugSurface.availableRoutingProviders()).containsExactly(
            RoutingProvider.OSRM,
            RoutingProvider.GRAPH_HOPPER,
            RoutingProvider.VALHALLA
        ).inOrder()
        assertThat(debugSurface.showProviderDiagnostics).isTrue()
        assertThat(debugSurface.showHeavyVehicleDimensions).isTrue()
        assertThat(debugSurface.showSoundtrackScaffolding).isTrue()
    }
}
