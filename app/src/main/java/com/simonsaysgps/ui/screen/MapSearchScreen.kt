package com.simonsaysgps.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonsaysgps.R
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.explore.SavedPlaceRecord
import com.simonsaysgps.ui.components.BrandTopBar
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.components.TopLevelDestination
import com.simonsaysgps.ui.components.TopLevelNavigationBar
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.theme.ElectricBlue
import com.simonsaysgps.ui.theme.NightSky
import com.simonsaysgps.ui.theme.Sun
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
            selectedLocation = null,
            routeGeometry = emptyList(),
            followCurrentLocation = false
        )
    }
) {
    Scaffold(
        modifier = Modifier.testTag(UiTestTags.MAP_SEARCH_SCREEN),
        containerColor = Color(0xFFFFF6D8),
        topBar = {
            BrandTopBar(
                title = "Simon Says GPS",
                subtitle = "Fun-first navigation.",
                badge = "Live map chaos",
                mascotResId = R.drawable.ic_launcher_adaptive_bitmap,
                actions = {
                    Surface(shape = RoundedCornerShape(18.dp), color = Sun.copy(alpha = 0.92f)) {
                        IconButton(onClick = onVoiceAssistantClick) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice assistant", tint = NightSky)
                        }
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = ElectricBlue.copy(alpha = 0.92f)) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSecondary)
                        }
                    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SearchCard(
                    state = state,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearch = onSearch,
                    onPlaceSelected = onPlaceSelected
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NightSky)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(356.dp)
                        ) {
                            mapContent(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SourcePill(
                                    label = if (state.settings.demoMode) "Demo route feed" else "Live GPS",
                                    detail = if (state.settings.demoMode) {
                                        "Using the built-in simulation path."
                                    } else if (state.currentLocation != null) {
                                        "Using your device location."
                                    } else {
                                        "Waiting for a location fix."
                                    }
                                )
                                if (!state.hasLocationPermission) {
                                    FilledTonalButton(onClick = onRequestLocationPermission) {
                                        Icon(Icons.Default.NearMe, contentDescription = null)
                                        Text("Enable live location")
                                    }
                                }
                            }
                            if (state.selectedPlace != null) {
                                SelectedPlaceDock(
                                    place = state.selectedPlace,
                                    onPreviewRoute = onPreviewRoute,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp)
                                )
                            }
                        }
                        HeroCard(
                            state = state,
                            onVoiceAssistantClick = onVoiceAssistantClick,
                            onExploreClick = onExploreClick,
                            onDismissOnboarding = onDismissOnboarding
                        )
                    }
                }
            }
            item {
                QuickActionsRow(
                    onVoiceAssistantClick = onVoiceAssistantClick,
                    onExploreClick = onExploreClick
                )
            }
            item {
                SearchResultsSection(
                    status = state.searchStatus,
                    searchResults = state.searchResults,
                    savedPlaces = state.savedPlaces,
                    recentDestinations = state.recentDestinations,
                    searchInFlight = state.searchInFlight,
                    searchError = state.searchError,
                    searchInfo = state.searchInfo,
                    onPlaceSelected = onPlaceSelected,
                    onRemoveRecentDestination = onRemoveRecentDestination,
                    onClearRecentDestinations = onClearRecentDestinations
                )
            }
            if (!state.settings.onboardingSeen) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(0.74f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("First look", style = MaterialTheme.typography.titleMedium)
                                Text("Search, pick a stop, preview the route, then let Simon talk you through it.", style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = onDismissOnboarding) { Text("Hide") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: AppUiState,
    onVoiceAssistantClick: () -> Unit,
    onExploreClick: () -> Unit,
    onDismissOnboarding: () -> Unit
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Misguided in the best way.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
        Text(
            "Search nearby, pick a playful destination, and let Simon turn a normal trip into a side quest.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
        )
        if (!state.settings.onboardingSeen) {
            TextButton(onClick = onDismissOnboarding) { Text("Hide intro") }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onVoiceAssistantClick: () -> Unit,
    onExploreClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickActionTile(
            title = "Voice",
            body = "Try Simon personalities and spoken prompts.",
            icon = { Icon(Icons.Default.Mic, contentDescription = null) },
            onClick = onVoiceAssistantClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            title = "Explore",
            body = "Let Simon pick a fun stop nearby.",
            icon = { Icon(Icons.Default.ArrowOutward, contentDescription = null) },
            onClick = onExploreClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    body: String,
    icon: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (title == "Voice") ElectricBlue else Color(0xFFFFD85C)
    val contentColor = if (title == "Voice") MaterialTheme.colorScheme.onPrimary else NightSky
    val iconContainer = if (title == "Voice") Color(0xFFBEEBFF) else Color(0xFFFFF3BF)
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = iconContainer) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = icon
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun SourcePill(label: String, detail: String) {
    Surface(color = MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(999.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = NightSky)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = NightSky.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SelectedPlaceDock(
    place: PlaceResult,
    onPreviewRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFF4CF),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ready for Simon", style = MaterialTheme.typography.labelLarge, color = ElectricBlue)
                    Text(place.name, style = MaterialTheme.typography.titleLarge, color = NightSky, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(place.fullAddress, style = MaterialTheme.typography.bodySmall, color = NightSky.copy(alpha = 0.74f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(onClick = onPreviewRoute, modifier = Modifier.fillMaxWidth()) {
                Text("Preview route")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchCard(
    state: AppUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlaceSelected: (PlaceResult) -> Unit
) {
    val dropdownExpanded = remember(state.searchQuery, state.searchResults, state.searchStatus) {
        mutableStateOf(state.searchQuery.isNotBlank() && state.searchResults.isNotEmpty() && state.searchStatus != SearchStatus.RECENTS)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2BF))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Find a stop", style = MaterialTheme.typography.titleLarge, color = NightSky)
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded.value,
                onExpandedChange = { shouldExpand ->
                    dropdownExpanded.value = shouldExpand && state.searchResults.isNotEmpty() && state.searchQuery.isNotBlank()
                }
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = {
                        dropdownExpanded.value = it.isNotBlank()
                        onSearchQueryChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Where to?") },
                    supportingText = {
                        Text(
                            when (state.searchStatus) {
                                SearchStatus.DEBOUNCING -> if (state.searchResults.isNotEmpty()) {
                                    "Recent and nearby suggestions are updating."
                                } else {
                                    "Holding for a moment before searching."
                                }
                                SearchStatus.LOADING -> if (state.searchResults.isNotEmpty()) {
                                    "Refreshing nearby suggestions as you type."
                                } else {
                                    "Searching nearby places."
                                }
                                SearchStatus.ERROR -> state.searchError ?: "Search failed."
                                SearchStatus.EMPTY -> state.searchInfo ?: "Try a street, place, or neighborhood."
                                SearchStatus.RECENTS -> "Recent places and saved picks appear here when search is empty."
                                SearchStatus.SUCCESS -> state.searchInfo ?: "Choose a place and preview the route."
                            }
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.searchInFlight) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded.value)
                            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        }
                    },
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded.value && state.searchResults.isNotEmpty(),
                    onDismissRequest = { dropdownExpanded.value = false }
                ) {
                    state.searchResults.take(6).forEach { place ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        place.fullAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                dropdownExpanded.value = false
                                onPlaceSelected(place)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    status: SearchStatus,
    searchResults: List<PlaceResult>,
    savedPlaces: List<SavedPlaceRecord>,
    recentDestinations: List<PlaceResult>,
    searchInFlight: Boolean,
    searchError: String?,
    searchInfo: String?,
    onPlaceSelected: (PlaceResult) -> Unit,
    onRemoveRecentDestination: (String) -> Unit,
    onClearRecentDestinations: () -> Unit
) {
    when (status) {
        SearchStatus.LOADING,
        SearchStatus.DEBOUNCING -> if (searchResults.isEmpty()) {
            LoadingCard(status)
        } else {
            MessageCard("Suggestions ready", if (searchInFlight) "Pick from the search dropdown above while nearby results continue updating." else "Pick from the search dropdown above.")
        }
        SearchStatus.SUCCESS -> {}
        SearchStatus.ERROR -> MessageCard("Search unavailable", searchError ?: "Something went wrong while contacting the geocoder.")
        SearchStatus.EMPTY -> if (searchResults.isEmpty()) {
            MessageCard("No matches yet", searchInfo ?: "Try a broader place name, city, or street address.")
        } else {
            MessageCard("Suggestions ready", "Pick from the search dropdown above.")
        }
        SearchStatus.RECENTS -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recentDestinations.isEmpty() && savedPlaces.isEmpty()) {
                    MessageCard("Nothing saved yet", "Pick a destination or save an Explore result and it will show up here for the next round.")
                } else {
                    if (savedPlaces.isNotEmpty()) {
                        Text("Saved places", style = MaterialTheme.typography.titleLarge)
                        savedPlaces.forEach { place ->
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent destinations", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = onClearRecentDestinations) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Text("Clear")
                            }
                        }
                        recentDestinations.forEach { place ->
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
private fun LoadingCard(status: SearchStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
            Text(
                if (status == SearchStatus.DEBOUNCING) {
                    "Holding for a moment so Simon does not spam the geocoder."
                } else {
                    "Searching for destinations."
                }
            )
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
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBED))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.invoke() ?: Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, color = NightSky, maxLines = 1, overflow = TextOverflow.Ellipsis)
                supportingLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ElectricBlue) }
                Text(place.fullAddress, style = MaterialTheme.typography.bodySmall, color = NightSky.copy(alpha = 0.74f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            action?.invoke() ?: Spacer(Modifier.size(1.dp))
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
private fun MessageCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0C7))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = NightSky)
            Text(body, color = NightSky.copy(alpha = 0.76f))
        }
    }
}
