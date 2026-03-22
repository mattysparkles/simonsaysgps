package com.simonsaysgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.util.DistanceFormatter
import com.simonsaysgps.ui.components.InfoCard
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RoutePreviewScreen(
    viewModel: AppViewModel,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    RoutePreviewScreenContent(
        state = state,
        onStartNavigation = {
            viewModel.startNavigation()
            onStartNavigation()
        },
        onBack = onBack
    )
}

@Composable
fun RoutePreviewScreenContent(
    state: AppUiState,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit = { modifier ->
        MapLibreMapView(
            modifier = modifier,
            currentLocation = state.currentLocation?.coordinate,
            selectedLocation = state.selectedPlace?.coordinate,
            routeGeometry = state.routePreview?.geometry.orEmpty()
        )
    }
) {
    val route = state.routePreview
    Scaffold(modifier = Modifier.testTag(UiTestTags.ROUTE_PREVIEW_SCREEN)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
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
            mapContent(Modifier.fillMaxWidth().height(300.dp))
            InfoCard("Distance", DistanceFormatter.format(route.totalDistanceMeters, state.settings.distanceUnit))
            InfoCard("ETA", DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochSecond(route.etaEpochSeconds).atZone(ZoneId.systemDefault())))
            InfoCard("Maneuvers", "${route.maneuvers.size} turns")
            state.routeInfo?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
