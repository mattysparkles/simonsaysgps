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
        settingsSummary = exploreSettingsSummary(state.settings.exploreSettings.defaultRadiusMiles, state.settings.exploreSettings.requireOpenNowByDefault),
        onDismissWalkthrough = viewModel::dismissExploreWalkthrough,
        onOpenSettings = onExploreSettings,
        onCategorySelected = {
            viewModel.loadExplore(it)
            onExploreResults()
        },
        onMapClick = onMapClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreenContent(
    walkthroughVisible: Boolean,
    selectedCategory: ExploreCategory?,
    settingsSummary: String,
    onDismissWalkthrough: () -> Unit,
    onOpenSettings: () -> Unit,
    onCategorySelected: (ExploreCategory) -> Unit,
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
                        Text("This first-run stub explains the foundation: choose an intent, review why picks were ranked, and tune safety and detour rules in settings.")
                        TextButton(onClick = onDismissWalkthrough) { Text("Got it") }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How Explore works", style = MaterialTheme.typography.titleMedium)
                    Text("Each chip maps to a ranking intent, not a promise of perfect data. The app combines distance, hours, reviews, novelty, route fit, and optional provider signals.")
                    Text(settingsSummary, style = MaterialTheme.typography.bodySmall)
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
