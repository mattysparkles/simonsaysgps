package com.simonsaysgps.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.util.DistanceFormatter
import com.simonsaysgps.ui.components.InfoCard
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun ActiveNavigationScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ActiveNavigationScreenContent(
        state = state,
        onBack = onBack,
        debugOverlay = { DebugOverlay(viewModel = viewModel) }
    )
}

@Composable
fun ActiveNavigationScreenContent(
    state: AppUiState,
    onBack: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit = { modifier ->
        MapLibreMapView(
            modifier = modifier,
            currentLocation = state.currentLocation?.coordinate,
            selectedLocation = state.selectedPlace?.coordinate,
            routeGeometry = state.navigationState.route?.geometry.orEmpty()
        )
    },
    debugOverlay: @Composable () -> Unit = { DebugOverlayContent(state.navigationState, state.currentLocation?.coordinate?.latitude, state.currentLocation?.coordinate?.longitude) }
) {
    val navigation = state.navigationState
    val route = navigation.route
    Scaffold(modifier = Modifier.testTag(UiTestTags.ACTIVE_NAVIGATION_SCREEN)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            mapContent(Modifier.fillMaxWidth().height(260.dp))
            InfoCard(
                title = "Next instruction",
                value = navigation.upcomingManeuver?.instruction ?: "Arriving soon",
                badgeText = if (navigation.upcomingManeuver?.authorization == ManeuverAuthorization.REQUIRED_SIMON_SAYS) "SIMON SAYS" else "INFO ONLY",
                badgeColor = if (navigation.upcomingManeuver?.authorization == ManeuverAuthorization.REQUIRED_SIMON_SAYS) Color(0xFFB7F5C5) else Color(0xFFFFE08A)
            )
            InfoCard(
                title = "Distance to next",
                value = navigation.distanceToNextManeuverMeters?.let { DistanceFormatter.format(it, state.settings.distanceUnit) } ?: "Done"
            )
            InfoCard(title = "Current road", value = navigation.upcomingManeuver?.roadName ?: "Following route")
            InfoCard(
                title = "ETA",
                value = route?.etaEpochSeconds?.let {
                    java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString()
                } ?: "--"
            )
            navigation.spokenPrompt?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            if (state.settings.debugMode) {
                debugOverlay()
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("End navigation")
            }
        }
    }
}

@Composable
private fun DebugOverlay(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    DebugOverlayContent(
        navigation = state.navigationState,
        latitude = state.currentLocation?.coordinate?.latitude,
        longitude = state.currentLocation?.coordinate?.longitude
    )
}

@Composable
private fun DebugOverlayContent(
    navigation: NavigationSessionState,
    latitude: Double?,
    longitude: Double?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.DEBUG_OVERLAY)
            .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Text(
            text = """
GPS: $latitude, $longitude
Step index: ${navigation.activeManeuverIndex}
Distance: ${navigation.distanceToNextManeuverMeters}
Simon auth: ${navigation.upcomingManeuver?.authorization}
Heading: ${navigation.headingDegrees}
Off-route: ${navigation.offRoute}
Last reroute: ${navigation.lastRerouteReason}
            """.trimIndent(),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
