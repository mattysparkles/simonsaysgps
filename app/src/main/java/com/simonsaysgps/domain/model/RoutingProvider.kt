package com.simonsaysgps.domain.model

enum class RoutingProvider(
    val displayName: String,
    val description: String
) {
    OSRM(
        displayName = "OSRM",
        description = "Open Source Routing Machine via the configured OSRM HTTP endpoint."
    ),
    GRAPH_HOPPER(
        displayName = "GraphHopper",
        description = "GraphHopper Directions API. Requires a configured API key."
    ),
    VALHALLA(
        displayName = "Valhalla",
        description = "Reserved for a future Valhalla adapter. Falls back gracefully today."
    );

    companion object {
        fun fromNameOrDefault(rawValue: String?, default: RoutingProvider = OSRM): RoutingProvider {
            return entries.firstOrNull { it.name.equals(rawValue, ignoreCase = true) } ?: default
        }
    }
}
