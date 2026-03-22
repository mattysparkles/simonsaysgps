package com.simonsaysgps.data.repository

import com.simonsaysgps.domain.model.RoutingProvider

data class RoutingProviderConfiguration(
    val defaultProvider: RoutingProvider,
    val graphHopperApiKey: String,
    val graphHopperProfile: String,
    val valhallaBaseUrl: String
)
