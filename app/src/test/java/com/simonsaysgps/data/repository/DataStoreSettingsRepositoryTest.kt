package com.simonsaysgps.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.RouteStyle
import com.simonsaysgps.domain.model.TransportProfile
import com.simonsaysgps.domain.model.VehicleProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreSettingsRepositoryTest {
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = DataStoreSettingsRepository(context)
        repository.update { it.copy() }
    }

    @Test
    fun `routing preferences persist transport profile and dimensions`() = runTest {
        repository.update { current ->
            current.copy(
                routingPreferences = current.routingPreferences.copy(
                    transportProfile = TransportProfile.TRUCK_COMMERCIAL,
                    primaryRouteStyle = RouteStyle.SCENIC,
                    avoidTolls = true,
                    preferLowStress = true,
                    simonChallengeMode = true,
                    challengeIntensity = 4,
                    vehicleProfile = VehicleProfile(heightMeters = 4.1, lengthMeters = 12.3, weightTons = 18.0)
                )
            )
        }

        val persisted = repository.settings.first()

        assertThat(persisted.routingPreferences.transportProfile).isEqualTo(TransportProfile.TRUCK_COMMERCIAL)
        assertThat(persisted.routingPreferences.primaryRouteStyle).isEqualTo(RouteStyle.SCENIC)
        assertThat(persisted.routingPreferences.vehicleProfile.heightMeters).isEqualTo(4.1)
        assertThat(persisted.routingPreferences.vehicleProfile.lengthMeters).isEqualTo(12.3)
        assertThat(persisted.routingPreferences.vehicleProfile.weightTons).isEqualTo(18.0)
        assertThat(persisted.routingPreferences.simonChallengeMode).isTrue()
    }

    @Test
    fun `explore privacy settings persist visit history controls`() = runTest {
        repository.update { current ->
            current.copy(
                exploreSettings = current.exploreSettings.copy(
                    visitHistoryEnabled = false,
                    visitHistoryRetentionDays = 90,
                    closeToHomeRadiusMiles = 12
                )
            )
        }

        val persisted = repository.settings.first()

        assertThat(persisted.exploreSettings.visitHistoryEnabled).isFalse()
        assertThat(persisted.exploreSettings.visitHistoryRetentionDays).isEqualTo(90)
        assertThat(persisted.exploreSettings.closeToHomeRadiusMiles).isEqualTo(12)
    }

    @Test
    fun `onboarding flag persists independently from release gating`() = runTest {
        repository.update { current -> current.copy(onboardingSeen = true) }

        val persisted = repository.settings.first()

        assertThat(persisted.onboardingSeen).isTrue()
    }

    @Test
    fun `voice assistant preferences persist safety toggles`() = runTest {
        repository.update { current ->
            current.copy(
                voiceAssistantSettings = current.voiceAssistantSettings.copy(
                    enabled = true,
                    handsFreeReportingEnabled = true,
                    voiceConfirmationRequired = false,
                    aiCleanupOptIn = true,
                    soundtrackIntegrationEnabled = true,
                    spokenConfirmationsEnabled = false
                )
            )
        }

        val persisted = repository.settings.first()

        assertThat(persisted.voiceAssistantSettings.voiceConfirmationRequired).isFalse()
        assertThat(persisted.voiceAssistantSettings.aiCleanupOptIn).isTrue()
        assertThat(persisted.voiceAssistantSettings.soundtrackIntegrationEnabled).isTrue()
        assertThat(persisted.voiceAssistantSettings.spokenConfirmationsEnabled).isFalse()
    }

}
