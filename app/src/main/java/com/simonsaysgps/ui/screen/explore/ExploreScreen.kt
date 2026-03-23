package com.simonsaysgps.ui.screen.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.SavedPlaceRecord
import com.simonsaysgps.ui.components.TopLevelDestination
import com.simonsaysgps.ui.components.TopLevelNavigationBar
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun ExploreScreen(
    viewModel: AppViewModel,
    onMapClick: () -> Unit,
    onExploreResults: () -> Unit,
    onExploreSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ExploreScreenContent(
        walkthroughVisible = state.explore.walkthroughVisible,
        selectedCategory = state.explore.selectedCategory,
        savedPlaces = state.savedPlaces,
        settingsSummary = exploreSettingsSummary(state.settings.exploreSettings.defaultRadiusMiles, state.settings.exploreSettings.requireOpenNowByDefault),
        onDismissWalkthrough = viewModel::dismissExploreWalkthrough,
        onOpenSettings = onExploreSettings,
        onCategorySelected = {
            viewModel.loadExplore(it)
            onExploreResults()
        },
        onUseSavedPlace = {
            viewModel.selectSavedPlace(it)
            onMapClick()
        },
        onMapClick = onMapClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreenContent(
    walkthroughVisible: Boolean,
    selectedCategory: ExploreCategory?,
    savedPlaces: List<SavedPlaceRecord>,
    settingsSummary: String,
    onDismissWalkthrough: () -> Unit,
    onOpenSettings: () -> Unit,
    onCategorySelected: (ExploreCategory) -> Unit,
    onUseSavedPlace: (SavedPlaceRecord) -> Unit,
    onMapClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Take me Somewhere…") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Explore settings")
                    }
                }
            )
        },
        bottomBar = {
            TopLevelNavigationBar(
                selected = TopLevelDestination.EXPLORE,
                onMapClick = onMapClick,
                onExploreClick = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (walkthroughVisible) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Explore first look", style = MaterialTheme.typography.titleMedium)
                        Text("Choose an intent chip, let Simon rank a few nearby ideas, and then check the 'why this was chosen' explanation before you commit. Explore is meant to feel playful, not random.")
                        Text("Saved places, local reviews, and your Explore rules stay on-device in this first release.")
                        TextButton(onClick = onDismissWalkthrough) { Text("Sounds good") }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How Explore works", style = MaterialTheme.typography.titleMedium)
                    Text("Each chip maps to a ranking intent, not a guarantee of perfect live data. Simon combines distance, hours, local reviews, novelty, route fit, and any provider signals that are actually available in this build.")
                    Text(settingsSummary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved places", style = MaterialTheme.typography.titleMedium)
                    if (savedPlaces.isEmpty()) {
                        Text("Saved favorites from Explore show up here for quick map preview or navigation entry. Nothing is synced to an account yet.")
                    } else {
                        savedPlaces.take(3).forEach { savedPlace ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(savedPlace.name, style = MaterialTheme.typography.titleSmall)
                                    Text(savedPlace.typeLabel, style = MaterialTheme.typography.bodySmall)
                                    Text(savedPlace.address, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { onUseSavedPlace(savedPlace) }) { Text("Use on map") }
                                }
                            }
                        }
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExploreCategory.entries.forEach { category ->
                    AssistChip(
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.displayName) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        trailingIcon = if (selectedCategory == category) ({ Text("•") }) else null
                    )
                }
            }
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Tune Explore settings")
            }
        }
    }
}

private fun exploreSettingsSummary(radiusMiles: Int, requireOpenNow: Boolean): String {
    return "Current defaults: ${radiusMiles}mi radius · ${if (requireOpenNow) "open-now favored" else "open-now optional"}."
}
