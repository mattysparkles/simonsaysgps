package com.simonsaysgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchScreen(
    viewModel: AppViewModel,
    onRouteReady: () -> Unit,
    onSettingsClick: () -> Unit,
    requestLocationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simon Says GPS") },
                actions = { IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.hasLocationPermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Location permission is needed for real navigation.")
                        Button(onClick = requestLocationPermission) { Text("Grant location") }
                    }
                }
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search destination") },
                trailingIcon = { IconButton(onClick = viewModel::search) { Icon(Icons.Default.Search, contentDescription = null) } }
            )
            MapLibreMapView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                currentLocation = state.currentLocation?.coordinate,
                selectedLocation = state.selectedPlace?.coordinate,
                routeGeometry = state.routePreview?.geometry.orEmpty()
            )
            state.selectedPlace?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(it.name, style = MaterialTheme.typography.titleMedium)
                        Text(it.fullAddress, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { viewModel.requestRoute(); onRouteReady() }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Preview route")
                        }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.searchResults, key = { it.id }) { place ->
                    Card(onClick = { viewModel.selectPlace(place) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            Text(place.fullAddress, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
