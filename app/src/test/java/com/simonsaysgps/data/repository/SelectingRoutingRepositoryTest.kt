package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.repository.ProviderRoutingRepository
import com.simonsaysgps.domain.repository.RoutingProviderAvailability
import com.simonsaysgps.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelectingRoutingRepositoryTest {
    @Test
    fun `uses selected provider when available`() = runTest {
        val osrm = FakeProviderRoutingRepository(RoutingProvider.OSRM)
        val graphHopper = FakeProviderRoutingRepository(RoutingProvider.GRAPH_HOPPER)
        val repository = SelectingRoutingRepository(
            settingsRepository = FakeSettingsRepository(SettingsModel(routingProvider = RoutingProvider.GRAPH_HOPPER)),
            repositories = setOf(osrm, graphHopper),
            configuration = RoutingProviderConfiguration(
                defaultProvider = RoutingProvider.OSRM,
                graphHopperApiKey = "key",
                graphHopperProfile = "car",
                valhallaBaseUrl = ""
            )
        )

        val result = repository.calculateRoute(Coordinate(1.0, 2.0), Coordinate(3.0, 4.0)) as RepositoryResult.Success

        assertThat(result.value.totalDistanceMeters).isEqualTo(25.0)
        assertThat(graphHopper.requestCount).isEqualTo(1)
        assertThat(osrm.requestCount).isEqualTo(0)
        assertThat(repository.resolveRepository(RoutingProvider.GRAPH_HOPPER).resolvedProvider).isEqualTo(RoutingProvider.GRAPH_HOPPER)
    }

    @Test
    fun `falls back to default provider when selected provider unavailable`() = runTest {
        val osrm = FakeProviderRoutingRepository(RoutingProvider.OSRM)
        val graphHopper = FakeProviderRoutingRepository(
            provider = RoutingProvider.GRAPH_HOPPER,
            available = false,
            reason = "GRAPH_HOPPER_API_KEY missing"
        )
        val repository = SelectingRoutingRepository(
            settingsRepository = FakeSettingsRepository(SettingsModel(routingProvider = RoutingProvider.GRAPH_HOPPER)),
            repositories = setOf(osrm, graphHopper),
            configuration = RoutingProviderConfiguration(
                defaultProvider = RoutingProvider.OSRM,
                graphHopperApiKey = "",
                graphHopperProfile = "car",
                valhallaBaseUrl = ""
            )
        )

        repository.calculateRoute(Coordinate(1.0, 2.0), Coordinate(3.0, 4.0))
        val resolution = repository.resolveRepository(RoutingProvider.GRAPH_HOPPER)

        assertThat(osrm.requestCount).isEqualTo(1)
        assertThat(graphHopper.requestCount).isEqualTo(0)
        assertThat(resolution.resolvedProvider).isEqualTo(RoutingProvider.OSRM)
        assertThat(resolution.reason).contains("GRAPH_HOPPER_API_KEY")
    }

    @Test
    fun `returns failure when requested provider unsupported and default unavailable`() = runTest {
        val repository = SelectingRoutingRepository(
            settingsRepository = FakeSettingsRepository(SettingsModel(routingProvider = RoutingProvider.VALHALLA)),
            repositories = emptySet(),
            configuration = RoutingProviderConfiguration(
                defaultProvider = RoutingProvider.OSRM,
                graphHopperApiKey = "",
                graphHopperProfile = "car",
                valhallaBaseUrl = ""
            )
        )

        val result = repository.calculateRoute(Coordinate(1.0, 2.0), Coordinate(3.0, 4.0)) as RepositoryResult.Failure

        assertThat(result.failure.detail).contains("Valhalla")
        assertThat(result.failure.detail).contains("OSRM")
    }

    private class FakeSettingsRepository(initial: SettingsModel) : SettingsRepository {
        private val backing = MutableStateFlow(initial)
        override val settings: Flow<SettingsModel> = backing

        override suspend fun update(transform: (SettingsModel) -> SettingsModel) {
            backing.value = transform(backing.value)
        }
    }

    private class FakeProviderRoutingRepository(
        override val provider: RoutingProvider,
        private val available: Boolean = true,
        private val reason: String? = null
    ) : ProviderRoutingRepository {
        var requestCount: Int = 0

        override fun availability(): RoutingProviderAvailability = RoutingProviderAvailability(available, reason)

        override suspend fun calculateRoute(origin: Coordinate, destination: Coordinate): RepositoryResult<Route> {
            requestCount += 1
            return RepositoryResult.Success(
                value = Route(
                    geometry = listOf(origin, destination),
                    maneuvers = emptyList(),
                    totalDistanceMeters = 25.0,
                    totalDurationSeconds = 30.0,
                    etaEpochSeconds = 1L
                ),
                source = FetchSource.NETWORK
            )
        }
    }
}
