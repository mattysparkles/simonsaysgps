package com.simonsaysgps.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.service.RoutingSupportAdvisor
import com.simonsaysgps.domain.util.DistanceFormatter
import com.simonsaysgps.ui.components.BrandTopBar
import com.simonsaysgps.ui.components.InfoCard
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.theme.ElectricBlue
import com.simonsaysgps.ui.theme.NightSky
import com.simonsaysgps.ui.theme.Sun
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RoutePreviewScreen(
    viewModel: AppViewModel,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    requestNotificationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    RoutePreviewScreenContent(
        state = state,
        onStartNavigation = {
            viewModel.startNavigation()
            onStartNavigation()
        },
        onBack = onBack,
        onRequestNotificationPermission = requestNotificationPermission
    )
}

@Composable
fun RoutePreviewScreenContent(
    state: AppUiState,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit = { modifier ->
        MapLibreMapView(
            modifier = modifier,
            currentLocation = state.currentLocation?.coordinate,
            selectedLocation = state.selectedPlace?.coordinate,
            routeGeometry = state.routePreview?.geometry.orEmpty(),
            followCurrentLocation = false
        )
    }
) {
    val route = state.routePreview
    Scaffold(
        modifier = Modifier.testTag(UiTestTags.ROUTE_PREVIEW_SCREEN),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrandTopBar(
                title = "Trip preview",
                subtitle = "Big picture first. Check the route shape before Simon starts narrating your choices.",
                badge = "Ready to roll"
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                if (route == null) {
                    Text(
                        state.routeError ?: "No route loaded yet.",
                        color = if (state.routeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                    )
                    return@Column
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NightSky)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(330.dp)
                        ) {
                            mapContent(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                tonalElevation = 6.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Trip preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        state.selectedPlace?.name ?: "Selected destination",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = NightSky
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PreviewPill("Distance", DistanceFormatter.format(route.totalDistanceMeters, state.settings.distanceUnit))
                                        PreviewPill("ETA", DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochSecond(route.etaEpochSeconds).atZone(ZoneId.systemDefault())))
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Trip feel", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                            Text(
                                "A tighter preview before you commit. Check the first turns, the route shape, and the overall vibe before Simon takes the mic.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    InfoCard("Maneuvers", "${route.maneuvers.size} turns", modifier = Modifier.weight(1f), badgeText = "Sharp")
                    InfoCard("Profile", state.settings.routingPreferences.transportProfile.displayName, modifier = Modifier.weight(1f), badgeText = "Mode", badgeColor = Sun)
                }
                InfoCard(
                    "Route style",
                    RoutingSupportAdvisor.plan(state.settings).requestedStyles.joinToString(),
                    badgeText = "Vibe",
                    badgeColor = ElectricBlue.copy(alpha = 0.18f)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Foreground trip behavior", style = MaterialTheme.typography.titleMedium, color = NightSky)
                        Text(
                            "Turn-by-turn guidance runs as a foreground service only while a trip is active. Simon does not request background location access for idle use.",
                            color = NightSky.copy(alpha = 0.82f)
                        )
                        if (!state.hasNotificationPermission) {
                            Text(
                                "Allow trip notifications so Android can keep the active navigation banner visible while guidance is running.",
                                color = NightSky.copy(alpha = 0.82f)
                            )
                            Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                                Text("Allow trip notifications")
                            }
                        } else {
                            Text(
                                "Trip notifications are allowed, so the active navigation notification can stay visible while guidance is running.",
                                color = NightSky.copy(alpha = 0.82f)
                            )
                        }
                    }
                }
                state.routeInfo?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = "Lane-by-lane hints only appear when the active routing provider actually supplies them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val advisory = RoutingSupportAdvisor.plan(state.settings).advisory
                if (advisory.limitations.isNotEmpty()) {
                    Text(
                        text = advisory.limitations.joinToString(separator = "\n") { "• $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                state.routeError?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = route.maneuvers.take(3).joinToString("\n") { "• ${it.instruction}" },
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onStartNavigation, modifier = Modifier.fillMaxWidth()) {
                    Text("Start navigation")
                }
            }
        }
    }
}

@Composable
private fun PreviewPill(title: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = NightSky)
        }
    }
}
