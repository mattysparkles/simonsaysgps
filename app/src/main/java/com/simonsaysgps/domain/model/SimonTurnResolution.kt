package com.simonsaysgps.domain.model

sealed class SimonTurnResolution {
    data object None : SimonTurnResolution()
    data class Authorized(val maneuver: RouteManeuver) : SimonTurnResolution()
    data class Unauthorized(val maneuver: RouteManeuver) : SimonTurnResolution()
    data class Missed(val maneuver: RouteManeuver) : SimonTurnResolution()
    data class OffRoute(val maneuver: RouteManeuver?) : SimonTurnResolution()
}
