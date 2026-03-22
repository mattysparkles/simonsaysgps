package com.simonsaysgps.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.PromptFrequency
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.ui.screen.ActiveNavigationScreenContent
import com.simonsaysgps.ui.screen.MapSearchScreenContent
import com.simonsaysgps.ui.screen.RoutePreviewScreenContent
import com.simonsaysgps.ui.screen.SettingsScreenContent
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.theme.SimonSaysGpsTheme
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.SearchStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyFlowsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun destinationSearch_flow_isCoveredWithDeterministicResults() {
        composeRule.setContent {
            TestTheme {
                MapSearchScreenContent(
                    state = sampleSearchState(),
                    onSearchQueryChange = {},
                    onSearch = {},
                    onPlaceSelected = {},
                    onPreviewRoute = {},
                    onSettingsClick = {},
                    onExploreClick = {},
                    onRequestLocationPermission = {},
                    onRemoveRecentDestination = {},
                    onClearRecentDestinations = {},
                    mapContent = ::FakeMapPanel
                )
            }
        }

        composeRule.onNodeWithText("Golden Gate Bridge").assertIsDisplayed()
        composeRule.onNodeWithText("Preview route").assertIsDisplayed()
        UiScreenshotCapture.capture(composeRule.onNodeWithTag(UiTestTags.MAP_SEARCH_SCREEN), "destination-search")
    }

    @Test
    fun routePreview_flow_showsRouteSummaryAndStartAction() {
        composeRule.setContent {
            TestTheme {
                RoutePreviewScreenContent(
                    state = sampleRoutePreviewState(),
                    onStartNavigation = {},
                    onBack = {},
                    mapContent = ::FakeMapPanel
                )
            }
        }

        composeRule.onNodeWithText("Distance").assertIsDisplayed()
        composeRule.onNodeWithText("Start navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Using the latest saved route because the network request timed out.").assertIsDisplayed()
        UiScreenshotCapture.capture(composeRule.onNodeWithTag(UiTestTags.ROUTE_PREVIEW_SCREEN), "route-preview")
    }

    @Test
    fun activeNavigation_flow_showsGuidanceAndDebugOverlayWhenEnabled() {
        composeRule.setContent {
            TestTheme {
                ActiveNavigationScreenContent(
                    state = sampleActiveNavigationState(debugMode = true),
                    onBack = {},
                    mapContent = ::FakeMapPanel
                )
            }
        }

        composeRule.onNodeWithText("Turn right onto Lombard Street").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.DEBUG_OVERLAY).assertIsDisplayed()
        UiScreenshotCapture.capture(composeRule.onNodeWithTag(UiTestTags.ACTIVE_NAVIGATION_SCREEN), "active-navigation")
    }

    @Test
    fun debugOverlay_isHiddenWhenDebugModeIsDisabled() {
        composeRule.setContent {
            TestTheme {
                ActiveNavigationScreenContent(
                    state = sampleActiveNavigationState(debugMode = false),
                    onBack = {},
                    mapContent = ::FakeMapPanel
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.DEBUG_OVERLAY).assertDoesNotExist()
        UiScreenshotCapture.capture(composeRule.onNodeWithTag(UiTestTags.ACTIVE_NAVIGATION_SCREEN), "active-navigation-no-debug")
    }

    @Test
    fun settings_flow_rendersDeterministicDemoFriendlyConfiguration() {
        composeRule.setContent {
            TestTheme {
                SettingsScreenContent(
                    state = sampleSettingsState(),
                    onBack = {},
                    onVoiceEnabledChange = {},
                    onDebugModeChange = {},
                    onDemoModeChange = {},
                    onRoutingProviderSelected = {},
                    onGameModeSelected = {},
                    onPromptFrequencySelected = {},
                    onPromptPersonalitySelected = {},
                    onDistanceUnitSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Debug overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Classic Simon").assertIsDisplayed()
        composeRule.onNodeWithText("Valhalla — Reserved for a future Valhalla adapter. Falls back gracefully today.").assertIsDisplayed()
        UiScreenshotCapture.capture(composeRule.onNodeWithTag(UiTestTags.SETTINGS_SCREEN), "settings")
    }

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        SimonSaysGpsTheme {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                content()
            }
        }
    }

    @Composable
    private fun FakeMapPanel(modifier: Modifier) {
        Box(
            modifier = modifier
                .testTag(UiTestTags.MAP_PLACEHOLDER)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9))
                    )
                )
        )
    }

    private fun sampleSearchState(): AppUiState {
        val selectedPlace = goldenGateBridge()
        return AppUiState(
            hasLocationPermission = true,
            searchQuery = "golden gate",
            searchStatus = SearchStatus.SUCCESS,
            searchInfo = "Select a destination to preview a route.",
            searchResults = listOf(
                selectedPlace,
                PlaceResult(
                    id = "pier-39",
                    name = "Pier 39",
                    fullAddress = "Pier 39, The Embarcadero, San Francisco, California, United States",
                    coordinate = Coordinate(37.8087, -122.4098)
                )
            ),
            selectedPlace = selectedPlace,
            currentLocation = sampleLocation(),
            routePreview = sampleRoute(),
            settings = sampleSettings()
        )
    }

    private fun sampleRoutePreviewState(): AppUiState = AppUiState(
        hasLocationPermission = true,
        selectedPlace = goldenGateBridge(),
        currentLocation = sampleLocation(),
        routePreview = sampleRoute(),
        routeInfo = "Using the latest saved route because the network request timed out.",
        settings = sampleSettings()
    )

    private fun sampleActiveNavigationState(debugMode: Boolean): AppUiState {
        val route = sampleRoute()
        return AppUiState(
            hasLocationPermission = true,
            selectedPlace = goldenGateBridge(),
            currentLocation = sampleLocation(),
            navigationState = NavigationSessionState(
                route = route,
                currentLocation = sampleLocation(),
                activeManeuverIndex = 1,
                distanceToNextManeuverMeters = 120.0,
                currentRoad = "Lombard Street",
                upcomingManeuver = route.maneuvers[1],
                spokenPrompt = "Simon says turn right onto Lombard Street in 400 feet.",
                offRoute = false,
                lastRerouteReason = RerouteReason.NONE,
                headingDegrees = 82.0,
                navigationActive = true
            ),
            settings = sampleSettings().copy(debugMode = debugMode)
        )
    }

    private fun sampleSettingsState(): AppUiState = AppUiState(
        settings = sampleSettings().copy(
            routingProvider = RoutingProvider.OSRM,
            promptFrequency = PromptFrequency.NORMAL,
            promptPersonality = PromptPersonality.CLASSIC_SIMON,
            gameMode = GameMode.BASIC,
            demoMode = true,
            debugMode = true
        )
    )

    private fun sampleSettings() = SettingsModel(
        voiceEnabled = true,
        gameMode = GameMode.BASIC,
        promptFrequency = PromptFrequency.NORMAL,
        promptPersonality = PromptPersonality.CLASSIC_SIMON,
        routingProvider = RoutingProvider.OSRM,
        debugMode = true,
        demoMode = true
    )

    private fun sampleRoute() = Route(
        geometry = listOf(
            Coordinate(37.7749, -122.4194),
            Coordinate(37.7936, -122.4836),
            Coordinate(37.8199, -122.4783)
        ),
        maneuvers = listOf(
            RouteManeuver(
                id = "depart",
                coordinate = Coordinate(37.7749, -122.4194),
                instruction = "Head northwest on Market Street",
                turnType = TurnType.DEPART,
                roadName = "Market Street",
                distanceFromPreviousMeters = 0.0,
                distanceToNextMeters = 350.0,
                authorization = ManeuverAuthorization.NORMAL_INFO_ONLY,
                headingBefore = 0.0,
                headingAfter = 315.0
            ),
            RouteManeuver(
                id = "turn-right",
                coordinate = Coordinate(37.8000, -122.4376),
                instruction = "Turn right onto Lombard Street",
                turnType = TurnType.RIGHT,
                roadName = "Lombard Street",
                distanceFromPreviousMeters = 350.0,
                distanceToNextMeters = 800.0,
                authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                headingBefore = 315.0,
                headingAfter = 20.0
            ),
            RouteManeuver(
                id = "arrive",
                coordinate = Coordinate(37.8199, -122.4783),
                instruction = "Arrive at Golden Gate Bridge",
                turnType = TurnType.ARRIVE,
                roadName = "Golden Gate Bridge",
                distanceFromPreviousMeters = 800.0,
                distanceToNextMeters = 0.0,
                authorization = ManeuverAuthorization.NORMAL_INFO_ONLY,
                headingBefore = 20.0,
                headingAfter = 20.0
            )
        ),
        totalDistanceMeters = 1150.0,
        totalDurationSeconds = 540.0,
        etaEpochSeconds = 1_710_000_000L
    )

    private fun sampleLocation() = LocationSample(
        coordinate = Coordinate(37.7749, -122.4194),
        accuracyMeters = 4f,
        bearing = 315f,
        speedMetersPerSecond = 8f,
        timestampMillis = 1_710_000_000_000L
    )

    private fun goldenGateBridge() = PlaceResult(
        id = "golden-gate-bridge",
        name = "Golden Gate Bridge",
        fullAddress = "Golden Gate Bridge, San Francisco, California, United States",
        coordinate = Coordinate(37.8199, -122.4783)
    )
}
