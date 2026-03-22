package com.simonsaysgps.ui.screen.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.explore.AccessiblePlacesPreference
import com.simonsaysgps.domain.model.explore.ExploreSettings
import com.simonsaysgps.domain.model.explore.ExploreSuggestionCount
import com.simonsaysgps.domain.model.explore.QuietPreferenceStrictness
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun ExploreSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ExploreSettingsScreenContent(
        settings = state.settings.exploreSettings,
        onBack = onBack,
        onSettingsChange = { transform -> viewModel.updateExploreSettings(transform) },
        onUseCurrentLocationAsHome = { viewModel.saveCurrentLocationAsExploreHome() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreSettingsScreenContent(
    settings: ExploreSettings,
    onBack: () -> Unit,
    onSettingsChange: ((ExploreSettings) -> ExploreSettings) -> Unit,
    onUseCurrentLocationAsHome: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SliderCard(
                title = "Default radius",
                value = settings.defaultRadiusMiles.toFloat(),
                range = 2f..30f,
                helper = "How far Explore should look by default.",
                label = "${settings.defaultRadiusMiles.toInt()} miles",
                onValueChange = { value -> onSettingsChange { it.copy(defaultRadiusMiles = value.toInt()) } }
            )
            ToggleCard("Require open now by default", settings.requireOpenNowByDefault, "Avoid closed suggestions unless a category like New or Important still has a strong reason.") {
                onSettingsChange { current -> current.copy(requireOpenNowByDefault = it) }
            }
            ChoiceCard(
                title = "Suggestions to return",
                options = listOf(
                    ExploreSuggestionCount.AUTO_PICK to "1 auto-pick",
                    ExploreSuggestionCount.THREE_CHOICES to "3 choices"
                ),
                selected = settings.suggestionCount,
                onSelected = { selected -> onSettingsChange { it.copy(suggestionCount = selected) } }
            )
            ToggleCard("Allow route detours while navigating", settings.allowRouteDetoursWhileNavigating, "Lets On My Way rank suggestions against the active trip.") {
                onSettingsChange { current -> current.copy(allowRouteDetoursWhileNavigating = it) }
            }
            SliderCard(
                title = "Max detour distance",
                value = settings.maxDetourDistanceMiles.toFloat(),
                range = 1f..20f,
                helper = "Hard cap for route detours.",
                label = "${"%.1f".format(settings.maxDetourDistanceMiles)} miles",
                onValueChange = { value -> onSettingsChange { it.copy(maxDetourDistanceMiles = value.toDouble()) } }
            )
            SliderCard(
                title = "Max detour time",
                value = settings.maxDetourMinutes.toFloat(),
                range = 5f..45f,
                helper = "Soft limit for route detour time.",
                label = "${settings.maxDetourMinutes} minutes",
                onValueChange = { value -> onSettingsChange { it.copy(maxDetourMinutes = value.toInt()) } }
            )
            ToggleCard("Use event data when available", settings.useEventDataWhenAvailable, "Allows event timing to boost Important, Special, and nearby discoveries.") {
                onSettingsChange { current -> current.copy(useEventDataWhenAvailable = it) }
            }
            ToggleCard("Use internal reviews first", settings.useInternalReviewsFirst, "Prefer first-party/community review quality when it exists.") {
                onSettingsChange { current -> current.copy(useInternalReviewsFirst = it) }
            }
            ToggleCard("Include third-party review summaries", settings.includeThirdPartyReviewSummariesWhenAvailable, "Allows blended external review summaries when the provider exists.") {
                onSettingsChange { current -> current.copy(includeThirdPartyReviewSummariesWhenAvailable = it) }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home reference", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(if (settings.homeCoordinate != null) "${settings.homeLabel.ifBlank { "Saved Home" }} · ${settings.homeCoordinate.latitude}, ${settings.homeCoordinate.longitude}" else "No home reference saved yet.")
                    TextButton(onClick = onUseCurrentLocationAsHome) { Text("Use current location as home") }
                }
            }
            SliderCard(
                title = "Surprise me weighting",
                value = settings.surpriseMeWeight,
                range = 0f..1f,
                helper = "Adds a little randomness so Explore can surface memorable options without becoming a black box.",
                label = "${(settings.surpriseMeWeight * 100).toInt()}%",
                onValueChange = { value -> onSettingsChange { it.copy(surpriseMeWeight = value) } }
            )
            ToggleCard("Kid-friendly only", settings.kidFriendlyOnly, "Filters out suggestions that are clearly not aimed at families.") {
                onSettingsChange { current -> current.copy(kidFriendlyOnly = it) }
            }
            ChoiceCard(
                title = "Quiet preference",
                options = QuietPreferenceStrictness.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selected = settings.quietPreferenceStrictness,
                onSelected = { selected -> onSettingsChange { it.copy(quietPreferenceStrictness = selected) } }
            )
            ChoiceCard(
                title = "Accessibility preference",
                options = AccessiblePlacesPreference.entries.map { pref -> pref to pref.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
                selected = settings.accessiblePlacesPreference,
                onSelected = { selected -> onSettingsChange { it.copy(accessiblePlacesPreference = selected) } }
            )
            ToggleCard("Avoid alcohol-focused venues", settings.avoidAlcoholFocusedVenues, "Helps keep Explore from surfacing bar-heavy recommendations.") {
                onSettingsChange { current -> current.copy(avoidAlcoholFocusedVenues = it) }
            }
            ToggleCard("Avoid adult-oriented venues", settings.avoidAdultOrientedVenues, "Safety-first filter for adult-oriented places.") {
                onSettingsChange { current -> current.copy(avoidAdultOrientedVenues = it) }
            }
        }
    }
}

@Composable
private fun ToggleCard(title: String, checked: Boolean, helper: String, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title)
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Text(helper)
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    helper: String,
    label: String,
    onValueChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title)
            Text(label)
            Text(helper)
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }
}

@Composable
private fun <T> ChoiceCard(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title)
            options.forEach { (value, label) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = selected == value, onClick = { onSelected(value) })
                    Text(label, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
