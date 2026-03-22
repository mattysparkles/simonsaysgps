package com.simonsaysgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.viewmodel.AppViewModel
import com.simonsaysgps.ui.viewmodel.SearchStatus

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
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
                supportingText = {
                    when (state.searchStatus) {
                        SearchStatus.DEBOUNCING -> Text("Waiting briefly before searching…")
                        SearchStatus.LOADING -> Text("Searching OpenStreetMap destinations…")
                        SearchStatus.ERROR -> Text(state.searchError ?: "Something went wrong while searching.")
                        SearchStatus.EMPTY -> Text("No destinations matched that search.")
                        SearchStatus.RECENTS -> Text("Recent destinations appear here when the search is empty.")
                        SearchStatus.SUCCESS -> Text("Select a destination to preview a route.")
                    }
                },
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
            SearchResultsSection(
                modifier = Modifier.weight(1f),
                status = state.searchStatus,
                searchResults = state.searchResults,
                recentDestinations = state.recentDestinations,
                searchError = state.searchError,
                onPlaceSelected = viewModel::selectPlace,
                onRemoveRecentDestination = viewModel::removeRecentDestination,
                onClearRecentDestinations = viewModel::clearRecentDestinations
            )
        }
    }
}

@Composable
private fun SearchResultsSection(
    modifier: Modifier = Modifier,
    status: SearchStatus,
    searchResults: List<PlaceResult>,
    recentDestinations: List<PlaceResult>,
    searchError: String?,
    onPlaceSelected: (PlaceResult) -> Unit,
    onRemoveRecentDestination: (String) -> Unit,
    onClearRecentDestinations: () -> Unit
) {
    when (status) {
        SearchStatus.LOADING,
        SearchStatus.DEBOUNCING -> {
            Card(modifier = modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (status == SearchStatus.DEBOUNCING) {
                            "Holding for a moment so Simon doesn't spam Nominatim."
                        } else {
                            "Searching for destinations…"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        SearchStatus.ERROR -> MessageCard(
            modifier = modifier,
            title = "Search unavailable",
            body = searchError ?: "Something went wrong while contacting the geocoder."
        )

        SearchStatus.EMPTY -> MessageCard(
            modifier = modifier,
            title = "No matches",
            body = "Try a broader place name, city, or street address."
        )

        SearchStatus.SUCCESS -> {
            LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResults, key = { it.id }) { place ->
                    DestinationCard(place = place, onSelect = { onPlaceSelected(place) })
                }
            }
        }

        SearchStatus.RECENTS -> {
            if (recentDestinations.isEmpty()) {
                MessageCard(
                    modifier = modifier,
                    title = "No recent destinations yet",
                    body = "Select a search result to keep it handy here for the next trip."
                )
            } else {
                LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, contentDescription = null)
                                Text("Recent destinations", style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = onClearRecentDestinations) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Text("Clear all")
                            }
                        }
                    }
                    items(recentDestinations, key = { it.id }) { place ->
                        DestinationCard(
                            place = place,
                            onSelect = { onPlaceSelected(place) },
                            action = {
                                IconButton(onClick = { onRemoveRecentDestination(place.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove recent destination")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    place: PlaceResult,
    onSelect: () -> Unit,
    action: @Composable (() -> Unit)? = null
) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    place.fullAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            action?.invoke()
        }
    }
}

@Composable
private fun MessageCard(
    modifier: Modifier = Modifier,
    title: String,
    body: String
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
