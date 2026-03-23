package com.simonsaysgps.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Map : Screen("map")
    data object Explore : Screen("explore")
    data object ExploreResults : Screen("explore-results")
    data object PlaceDetail : Screen("place-detail")
    data object PlaceReviews : Screen("place-reviews")
    data object LeavePlaceReview : Screen("leave-place-review")
    data object ExploreSettings : Screen("explore-settings")
    data object RoutePreview : Screen("route-preview")
    data object Navigation : Screen("navigation")
    data object Settings : Screen("settings")
    data object VoiceAssistant : Screen("voice-assistant")
}
