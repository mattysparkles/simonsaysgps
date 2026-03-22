package com.simonsaysgps.domain.model

enum class TransportProfile(val displayName: String, val helperText: String) {
    WALKING("Walking", "Pedestrian-oriented trips and short on-foot routes."),
    BICYCLE("Bicycle", "A standard bicycle profile for bike-friendly routing when supported."),
    E_BIKE("E-bike", "An electric bicycle profile that can reuse bike-safe routing when a provider supports it."),
    E_SKATEBOARD("E-skateboard", "Micromobility trips where provider support is still limited today."),
    MOTORCYCLE("Motorcycle", "Road routing with motorcycle-oriented preferences when a provider can honor them."),
    CAR("Car", "Standard car routing and the safest default for current providers."),
    RV("RV", "Large recreational vehicle routing with size-aware restriction scaffolding."),
    TRUCK_COMMERCIAL("Truck / commercial", "Commercial vehicle routing with future restriction-aware provider hooks."),
    TRAILER_TOWING("Trailer / towing", "Passenger vehicle towing a trailer, camper, or cargo load.")
}

enum class RouteStyle(val displayName: String, val helperText: String) {
    FASTEST("Fastest", "Prioritize the quickest practical route when supported."),
    SCENIC("Scenic", "Prefer more scenic-feeling roads without intentionally creating wasteful loops."),
    NO_TOLLS("No tolls", "Ask for toll avoidance when the provider can honor it."),
    LOW_STRESS("Low stress", "Prefer calmer routing when the provider exposes a low-stress option."),
    SIMON_CHALLENGE("Simon Challenge Mode", "A playful preference for more turns, but still bounded and sane.")
}

data class VehicleProfile(
    val heightMeters: Double? = null,
    val lengthMeters: Double? = null,
    val weightTons: Double? = null
) {
    fun hasSafetyCriticalRestrictions(): Boolean {
        return listOf(heightMeters, lengthMeters, weightTons).any { value -> value != null && value > 0.0 }
    }
}

data class RoutingPreferences(
    val transportProfile: TransportProfile = TransportProfile.CAR,
    val primaryRouteStyle: RouteStyle = RouteStyle.FASTEST,
    val avoidTolls: Boolean = false,
    val preferScenic: Boolean = false,
    val preferFastest: Boolean = true,
    val preferLowStress: Boolean = false,
    val simonChallengeMode: Boolean = false,
    val challengeIntensity: Int = 2,
    val vehicleProfile: VehicleProfile = VehicleProfile()
)
