package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.RoutingProvider

interface ProviderRoutingRepository : RoutingRepository {
    val provider: RoutingProvider
    fun availability(): RoutingProviderAvailability
}

data class RoutingProviderAvailability(
    val available: Boolean,
    val reason: String? = null
)
