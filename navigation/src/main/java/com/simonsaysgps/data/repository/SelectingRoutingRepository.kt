package com.simonsaysgps.data.repository

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.NetworkFailure
import com.simonsaysgps.domain.model.NetworkFailureType
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.repository.ProviderRoutingRepository
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SelectingRoutingRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    repositories: Set<@JvmSuppressWildcards ProviderRoutingRepository>,
    private val configuration: RoutingProviderConfiguration
) : RoutingRepository {
    private val repositoriesByProvider = repositories.associateBy { it.provider }

    override suspend fun calculateRoute(origin: Coordinate, destination: Coordinate): RepositoryResult<Route> {
        val requestedProvider = settingsRepository.settings.first().routingProvider
        val resolution = resolveRepository(requestedProvider)
        val repository = resolution.repository
            ?: return RepositoryResult.Failure(
                NetworkFailure(
                    type = NetworkFailureType.SERVER,
                    detail = resolution.reason ?: "No supported routing provider is configured."
                )
            )
        return repository.calculateRoute(origin, destination)
    }

    internal fun resolveRepository(requestedProvider: RoutingProvider): RoutingSelection {
        val requestedRepository = repositoriesByProvider[requestedProvider]
        val requestedAvailability = requestedRepository?.availability()
        if (requestedAvailability?.available == true) {
            return RoutingSelection(requestedProvider, requestedProvider, requestedRepository, null)
        }

        val fallbackProvider = configuration.defaultProvider
        val fallbackRepository = repositoriesByProvider[fallbackProvider]
        val fallbackAvailability = fallbackRepository?.availability()
        if (requestedProvider != fallbackProvider && fallbackAvailability?.available == true) {
            val reason = requestedAvailability?.reason
                ?: "${requestedProvider.displayName} is not implemented in this build. Falling back to ${fallbackProvider.displayName}."
            return RoutingSelection(requestedProvider, fallbackProvider, fallbackRepository, reason)
        }

        val unavailableReason = requestedAvailability?.reason
            ?: if (requestedRepository == null) {
                "${requestedProvider.displayName} is not implemented in this build."
            } else {
                "${requestedProvider.displayName} is currently unavailable."
            }
        val fallbackReason = when {
            requestedProvider == fallbackProvider -> null
            fallbackRepository == null -> " ${fallbackProvider.displayName} is also not implemented in this build."
            fallbackAvailability?.available == false -> " ${fallbackProvider.displayName} is unavailable: ${fallbackAvailability.reason}."
            else -> null
        }
        return RoutingSelection(
            requestedProvider = requestedProvider,
            resolvedProvider = null,
            repository = null,
            reason = unavailableReason + (fallbackReason ?: "")
        )
    }
}

data class RoutingSelection(
    val requestedProvider: RoutingProvider,
    val resolvedProvider: RoutingProvider?,
    val repository: ProviderRoutingRepository?,
    val reason: String?
)
