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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.explore.SavedPlaceRecord
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.components.TopLevelDestination
import com.simonsaysgps.ui.components.TopLevelNavigationBar
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel
import com.simonsaysgps.ui.viewmodel.SearchStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchScreen(
    viewModel: AppViewModel,
    onRouteReady: () -> Unit,
    onSettingsClick: () -> Unit,
    onExploreClick: () -> Unit,
    onVoiceAssistantClick: () -> Unit,
    requestLocationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    MapSearchScreenContent(
        state = state,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearch = viewModel::search,
        onPlaceSelected = viewModel::selectPlace,
        onPreviewRoute = {
            viewModel.requestRoute()
            onRouteReady()
        },
        onSettingsClick = onSettingsClick,
        onExploreClick = onExploreClick,
        onVoiceAssistantClick = onVoiceAssistantClick,
        onRequestLocationPermission = requestLocationPermission,
        onRemoveRecentDestination = viewModel::removeRecentDestination,
        onClearRecentDestinations = viewModel::clearRecentDestinations,
        onDismissOnboarding = viewModel::dismissOnboarding
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchScreenContent(
    state: AppUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlaceSelected: (PlaceResult) -> Unit,
    onPreviewRoute: () -> Unit,
    onSettingsClick: () -> Unit,
    onExploreClick: () -> Unit,
    onVoiceAssistantClick: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRemoveRecentDestination: (String) -> Unit,
    onClearRecentDestinations: () -> Unit,
    onDismissOnboarding: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit = { modifier ->
        MapLibreMapView(
            modifier = modifier,
            currentLocation = state.currentLocation?.coordinate,
            selectedLocation = state.selectedPlace?.coordinate,
            routeGeometry = state.routePreview?.geometry.orEmpty()
        )
    }
) {
    Scaffold(
        modifier = Modifier.testTag(UiTestTags.MAP_SEARCH_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("Simon Says GPS") },
                actions = {
                    IconButton(onClick = onVoiceAssistantClick) { Icon(Icons.Default.Mic, contentDescription = "Voice assistant") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = null) }
                }
            )
        },
        bottomBar = {
            TopLevelNavigationBar(
                selected = TopLevelDestination.MAP,
                onMapClick = {},
                onExploreClick = onExploreClick
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
            if (!state.settings.onboardingSeen) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How the Simon Says game works", style = MaterialTheme.typography.titleMedium)
                        Text("1. Search for a destination and preview the route first.")
                        Text("2. Once navigation starts, Simon highlights the next Simon-approved move instead of every possible turn.")
                        Text("3. Keep following the approved move and the app reroutes if the joke goes too far.")
                        Text("The goal is playful navigation, not surprise detours that feel broken.")
                        TextButton(onClick = onDismissOnboarding) { Text("Let's drive") }
                    }
                }
            }
            if (!state.hasLocationPermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Location permission keeps Simon honest.", style = MaterialTheme.typography.titleMedium)
                        Text("We use location only to place you on the route, time the next Simon Says instruction, and recover if you miss a turn. Without it, you can still browse destinations but not run live navigation.")
                        Button(onClick = onRequestLocationPermission) { Text("Enable location for navigation") }
                    }
                }
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search destination") },
                supportingText = {
                    when (state.searchStatus) {
                        SearchStatus.DEBOUNCING -> Text("Waiting briefly before searching…")
                        SearchStatus.LOADING -> Text("Searching OpenStreetMap destinations…")
                        SearchStatus.ERROR -> Text(state.searchError ?: "Something went wrong while searching.")
                        SearchStatus.EMPTY -> Text(state.searchInfo ?: "No destinations matched that search.")
                        SearchStatus.RECENTS -> Text("Recent destinations and saved Explore picks appear here when the search is empty.")
                        SearchStatus.SUCCESS -> Text(state.searchInfo ?: "Select a destination, then preview the route before Simon takes over.")
                    }
                },
                trailingIcon = { IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = null) } }
            )
            Button(onClick = onVoiceAssistantClick, modifier = Modifier.fillMaxWidth()) { Text("Open voice assistant for commands, reports, and local review drafts") }
            mapContent(Modifier.fillMaxWidth().height(280.dp))
            state.selectedPlace?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(it.name, style = MaterialTheme.typography.titleMedium)
                        Text(it.fullAddress, style = MaterialTheme.typography.bodyMedium)
                        Text("Preview the route to learn Simon's first approved move before you start driving.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onPreviewRoute, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Preview route")
                        }
                    }
                }
            }
            SearchResultsSection(
                modifier = Modifier.weight(1f),
                status = state.searchStatus,
                searchResults = state.searchResults,
                savedPlaces = state.savedPlaces,
                recentDestinations = state.recentDestinations,
                searchError = state.searchError,
                searchInfo = state.searchInfo,
                onPlaceSelected = onPlaceSelected,
                onRemoveRecentDestination = onRemoveRecentDestination,
                onClearRecentDestinations = onClearRecentDestinations
            )
        }
    }
}

@Composable
private fun SearchResultsSection(
    modifier: Modifier = Modifier,
    status: SearchStatus,
    searchResults: List<PlaceResult>,
    savedPlaces: List<SavedPlaceRecord>,
    recentDestinations: List<PlaceResult>,
    searchError: String?,
    searchInfo: String?,
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
            title = "No matches yet",
            body = searchInfo ?: "Try a broader place name, city, or street address."
        )

        SearchStatus.SUCCESS -> {
            LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResults, key = { it.id }) { place ->
                    DestinationCard(place = place, onSelect = { onPlaceSelected(place) })
                }
            }
        }

        SearchStatus.RECENTS -> {
            if (recentDestinations.isEmpty() && savedPlaces.isEmpty()) {
                MessageCard(
                    modifier = modifier,
                    title = "Nothing saved yet",
                    body = "Pick a destination or save an Explore result and it will show up here for the next round."
                )
            } else {
                LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (savedPlaces.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Saved places", style = MaterialTheme.typography.titleMedium)
                                Text("${savedPlaces.size}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(savedPlaces, key = { it.canonicalPlaceId }) { place ->
                            DestinationCard(
                                place = PlaceResult(
                                    id = place.canonicalPlaceId,
                                    name = place.name,
                                    fullAddress = place.address,
                                    coordinate = place.coordinate
                                ),
                                supportingLabel = place.typeLabel,
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                onSelect = { onPlaceSelected(place.toPlaceResult()) }
                            )
                        }
                    }
                    if (recentDestinations.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent destinations", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = onClearRecentDestinations) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Text("Clear")
                                }
                            }
                        }
                        items(recentDestinations, key = { it.id }) { place ->
                            DestinationCard(
                                place = place,
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                action = {
                                    IconButton(onClick = { onRemoveRecentDestination(place.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove recent destination")
                                    }
                                },
                                onSelect = { onPlaceSelected(place) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    place: PlaceResult,
    supportingLabel: String? = null,
    icon: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    onSelect: () -> Unit
) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                supportingLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(place.fullAddress, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            action?.invoke()
        }
    }
}

private fun SavedPlaceRecord.toPlaceResult(): PlaceResult = PlaceResult(
    id = canonicalPlaceId,
    name = name,
    fullAddress = address,
    coordinate = coordinate
)

@Composable
private fun MessageCard(modifier: Modifier = Modifier, title: String, body: String) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}
