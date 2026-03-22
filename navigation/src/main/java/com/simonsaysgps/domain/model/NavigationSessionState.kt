package com.simonsaysgps.domain.model

data class NavigationSessionState(
    val route: Route? = null,
    val currentLocation: LocationSample? = null,
    val snappedLocation: Coordinate? = null,
    val activeManeuverIndex: Int = 0,
    val distanceToNextManeuverMeters: Double? = null,
    val currentRoad: String? = null,
    val upcomingManeuver: RouteManeuver? = null,
    val spokenPrompt: String? = null,
    val latestResolution: SimonTurnResolution = SimonTurnResolution.None,
    val offRoute: Boolean = false,
    val lastRerouteReason: RerouteReason = RerouteReason.NONE,
    val headingDegrees: Double? = null,
    val navigationActive: Boolean = false
)
