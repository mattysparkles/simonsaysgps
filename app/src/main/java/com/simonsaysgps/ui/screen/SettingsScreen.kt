package com.simonsaysgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simonsaysgps.config.ReleaseSurface
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.PromptFrequency
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RouteStyle
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.TransportProfile
import com.simonsaysgps.domain.service.RoutingSupportAdvisor
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    SettingsScreenContent(
        state = state,
        releaseSurface = ReleaseSurface.fromBuildConfig(),
        onBack = onBack,
        onVoiceEnabledChange = { value -> viewModel.updateSettings { it.copy(voiceEnabled = value) } },
        onDebugModeChange = { value -> viewModel.updateSettings { it.copy(debugMode = value) } },
        onDemoModeChange = { value -> viewModel.updateSettings { it.copy(demoMode = value) } },
        onRoutingProviderSelected = { selected -> viewModel.updateSettings { it.copy(routingProvider = RoutingProvider.valueOf(selected)) } },
        onGameModeSelected = { selected -> viewModel.updateSettings { current -> current.copy(gameMode = GameMode.valueOf(selected)) } },
        onPromptFrequencySelected = { selected -> viewModel.updateSettings { it.copy(promptFrequency = PromptFrequency.valueOf(selected)) } },
        onPromptPersonalitySelected = { selected -> viewModel.updateSettings { it.copy(promptPersonality = PromptPersonality.valueOf(selected)) } },
        onDistanceUnitSelected = { selected -> viewModel.updateSettings { it.copy(distanceUnit = DistanceUnit.valueOf(selected)) } },
        onTransportProfileSelected = { selected -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(transportProfile = TransportProfile.valueOf(selected))) } },
        onPrimaryRouteStyleSelected = { selected -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(primaryRouteStyle = RouteStyle.valueOf(selected))) } },
        onAvoidTollsChanged = { enabled -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(avoidTolls = enabled)) } },
        onPreferScenicChanged = { enabled -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(preferScenic = enabled)) } },
        onPreferFastestChanged = { enabled -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(preferFastest = enabled)) } },
        onPreferLowStressChanged = { enabled -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(preferLowStress = enabled)) } },
        onSimonChallengeModeChanged = { enabled -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(simonChallengeMode = enabled)) } },
        onChallengeIntensityChanged = { value -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(challengeIntensity = value.toInt())) } },
        onVehicleHeightChanged = { value -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(vehicleProfile = current.routingPreferences.vehicleProfile.copy(heightMeters = value.takeIf { it > 0f }?.toDouble()))) } },
        onVehicleLengthChanged = { value -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(vehicleProfile = current.routingPreferences.vehicleProfile.copy(lengthMeters = value.takeIf { it > 0f }?.toDouble()))) } },
        onVehicleWeightChanged = { value -> viewModel.updateSettings { current -> current.copy(routingPreferences = current.routingPreferences.copy(vehicleProfile = current.routingPreferences.vehicleProfile.copy(weightTons = value.takeIf { it > 0f }?.toDouble()))) } },
        onVoiceAssistantEnabledChange = { enabled -> viewModel.updateSettings { current -> current.copy(voiceAssistantSettings = current.voiceAssistantSettings.copy(enabled = enabled)) } },
        onHandsFreeReportingChange = { enabled -> viewModel.updateSettings { current -> current.copy(voiceAssistantSettings = current.voiceAssistantSettings.copy(handsFreeReportingEnabled = enabled)) } },
        onVoiceConfirmationChange = { enabled -> viewModel.updateSettings { current -> current.copy(voiceAssistantSettings = current.voiceAssistantSettings.copy(voiceConfirmationRequired = enabled)) } },
        onAiCleanupOptInChange = { enabled -> viewModel.updateSettings { current -> current.copy(voiceAssistantSettings = current.voiceAssistantSettings.copy(aiCleanupOptIn = enabled)) } },
        onSoundtrackIntegrationChange = { enabled -> viewModel.updateSettings { current -> current.copy(voiceAssistantSettings = current.voiceAssistantSettings.copy(soundtrackIntegrationEnabled = enabled)) } },
        onSpokenConfirmationsChange = { enabled -> viewModel.updateSettings { current -> current.copy(voiceAssistantSettings = current.voiceAssistantSettings.copy(spokenConfirmationsEnabled = enabled)) } }
    )
}

@Composable
fun SettingsScreenContent(
    state: AppUiState,
    releaseSurface: ReleaseSurface,
    onBack: () -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onDebugModeChange: (Boolean) -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onRoutingProviderSelected: (String) -> Unit,
    onGameModeSelected: (String) -> Unit,
    onPromptFrequencySelected: (String) -> Unit,
    onPromptPersonalitySelected: (String) -> Unit,
    onDistanceUnitSelected: (String) -> Unit,
    onTransportProfileSelected: (String) -> Unit,
    onPrimaryRouteStyleSelected: (String) -> Unit,
    onAvoidTollsChanged: (Boolean) -> Unit,
    onPreferScenicChanged: (Boolean) -> Unit,
    onPreferFastestChanged: (Boolean) -> Unit,
    onPreferLowStressChanged: (Boolean) -> Unit,
    onSimonChallengeModeChanged: (Boolean) -> Unit,
    onChallengeIntensityChanged: (Float) -> Unit,
    onVehicleHeightChanged: (Float) -> Unit,
    onVehicleLengthChanged: (Float) -> Unit,
    onVehicleWeightChanged: (Float) -> Unit,
    onVoiceAssistantEnabledChange: (Boolean) -> Unit,
    onHandsFreeReportingChange: (Boolean) -> Unit,
    onVoiceConfirmationChange: (Boolean) -> Unit,
    onAiCleanupOptInChange: (Boolean) -> Unit,
    onSoundtrackIntegrationChange: (Boolean) -> Unit,
    onSpokenConfirmationsChange: (Boolean) -> Unit
) {
    val settings = state.settings
    val routing = settings.routingPreferences
    val advisory = RoutingSupportAdvisor.plan(settings).advisory
    val routingProviderOptions = releaseSurface.availableRoutingProviders().map { provider ->
        provider.name to when (provider) {
            RoutingProvider.OSRM -> "${provider.displayName} — stable default for this release."
            RoutingProvider.GRAPH_HOPPER -> "${provider.displayName} — alternate provider${if (releaseSurface.releaseSafeSurface) " when configured for this build" else " for testing and comparison"}."
            RoutingProvider.VALHALLA -> "${provider.displayName} — developer-only placeholder while the adapter is still unfinished."
        }
    }

    Scaffold(modifier = Modifier.testTag(UiTestTags.SETTINGS_SCREEN)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            MessageCard(
                title = "Release honesty",
                body = if (releaseSurface.releaseSafeSurface) {
                    "This release keeps unfinished routing/provider experiments out of the main surface so the Simon Says core stays clear and reliable. The app requests location only for active trip guidance, microphone only for user-started voice capture, and no background location permission."
                } else {
                    "Developer build: experimental provider picks, soundtrack scaffolding, and diagnostics stay visible for testing."
                }
            )
            ToggleCard("Voice prompts", settings.voiceEnabled, "Read turn prompts aloud while navigating.", onVoiceEnabledChange)
            ToggleCard("Voice assistant", settings.voiceAssistantSettings.enabled, "Enable user-started voice input for search, reporting, and review drafting. Turning this off leaves typed commands available and prevents microphone capture from this feature.", onVoiceAssistantEnabledChange)
            ToggleCard("Hands-free reporting", settings.voiceAssistantSettings.handsFreeReportingEnabled, "Allow report staging by voice, but still require an explicit confirmation step before submission.", onHandsFreeReportingChange)
            ToggleCard("Voice confirmation", settings.voiceAssistantSettings.voiceConfirmationRequired, "Require a yes/no confirmation before Simon submits a crowd report.", onVoiceConfirmationChange)
            ToggleCard("Spoken confirmations", settings.voiceAssistantSettings.spokenConfirmationsEnabled, "Speak back short acknowledgements after voice actions to reduce glance time.", onSpokenConfirmationsChange)
            ToggleCard("Review cleanup suggestions", settings.voiceAssistantSettings.aiCleanupOptIn, "Offer optional cleanup suggestions for dictated reviews. Suggestions stay local to this flow and are never treated as published reviews automatically.", onAiCleanupOptInChange)
            if (releaseSurface.showSoundtrackScaffolding) {
                ToggleCard("Soundtrack scaffolding", settings.voiceAssistantSettings.soundtrackIntegrationEnabled, "Store playlist intent requests for future providers without assuming a specific music SDK today.", onSoundtrackIntegrationChange)
            } else {
                MessageCard(
                    title = "Music requests are unavailable in this release",
                    body = "The release build keeps soundtrack-provider controls hidden so voice stays focused on navigation, reports, and review drafting."
                )
            }
            if (releaseSurface.showDebugOverlayControls) {
                ToggleCard("Debug overlay", settings.debugMode, "Show extra diagnostics useful while testing providers and game logic.", onDebugModeChange)
            }
            if (releaseSurface.showDeveloperOptions) {
                ToggleCard("Demo mode", settings.demoMode, "Use the built-in demo location feed for emulator and screenshot testing.", onDemoModeChange)
            }
            ChoiceCard(
                title = "Routing provider",
                helper = if (releaseSurface.releaseSafeSurface) {
                    "Only providers configured for this release are shown here. Unsupported picks fall back to OSRM."
                } else {
                    "Choose the backend used for route calculation. Unsupported selections still fall back honestly."
                },
                options = routingProviderOptions,
                selected = settings.routingProvider.name,
                onSelected = onRoutingProviderSelected
            )
            ChoiceCard(
                title = "Game mode",
                helper = "Controls how mischievous Simon becomes while keeping the navigation layer usable.",
                options = listOf(GameMode.BASIC.name to "Basic", GameMode.MISCHIEF.name to "Mischief"),
                selected = settings.gameMode.name,
                onSelected = onGameModeSelected
            )
            ChoiceCard(
                title = "Prompt frequency",
                helper = "Adjust how often Simon speaks up during navigation.",
                options = PromptFrequency.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selected = settings.promptFrequency.name,
                onSelected = onPromptFrequencySelected
            )
            ChoiceCard(
                title = "Prompt personality",
                helper = "Switch the tone Simon uses for spoken and on-screen callouts.",
                options = PromptPersonality.entries.map { it.name to it.displayName },
                selected = settings.promptPersonality.name,
                onSelected = onPromptPersonalitySelected
            )
            ChoiceCard(
                title = "Units",
                helper = "Choose the distance units shown in preview and active navigation.",
                options = DistanceUnit.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selected = settings.distanceUnit.name,
                onSelected = onDistanceUnitSelected
            )
            ChoiceCard(
                title = "Transport profile",
                helper = if (releaseSurface.showHeavyVehicleDimensions) {
                    "Pick the vehicle or travel mode you want the routing layer to target. Developer builds expose saved size/weight scaffolding below."
                } else {
                    "Pick the vehicle or travel mode you want the routing layer to target. Large-vehicle size enforcement is not guaranteed in this release."
                },
                options = TransportProfile.entries.map { it.name to "${it.displayName} — ${it.helperText}" },
                selected = routing.transportProfile.name,
                onSelected = onTransportProfileSelected
            )
            ChoiceCard(
                title = "Primary route style",
                helper = "Choose the main route style. Additional toggles below stay bounded and never fake guaranteed provider support.",
                options = RouteStyle.entries.map { it.name to "${it.displayName} — ${it.helperText}" },
                selected = routing.primaryRouteStyle.name,
                onSelected = onPrimaryRouteStyleSelected
            )
            ToggleCard("Avoid tolls", routing.avoidTolls, "Request toll avoidance when the provider can honor it.", onAvoidTollsChanged)
            ToggleCard("Prefer scenic roads", routing.preferScenic, "Ask for a more scenic-feeling route without intentionally creating wasteful loops.", onPreferScenicChanged)
            ToggleCard("Prefer fastest route", routing.preferFastest, "Keep the routing layer biased toward the fastest practical route.", onPreferFastestChanged)
            ToggleCard("Prefer calmer roads", routing.preferLowStress, "Use a calmer-road preference if the provider supports one.", onPreferLowStressChanged)
            ToggleCard("Simon Challenge Mode", routing.simonChallengeMode, "Playfully ask for more turn variety while staying bounded and sane.", onSimonChallengeModeChanged)
            SliderCard(
                title = "Challenge intensity",
                value = routing.challengeIntensity.toFloat(),
                range = 1f..5f,
                helper = "Higher values ask for slightly more route variety, but never absurd loops.",
                label = routing.challengeIntensity.toString(),
                onValueChange = onChallengeIntensityChanged
            )
            if (releaseSurface.showHeavyVehicleDimensions) {
                SliderCard(
                    title = "Vehicle height",
                    value = (routing.vehicleProfile.heightMeters ?: 0.0).toFloat(),
                    range = 0f..5f,
                    helper = "Set to zero if not relevant. These dimensions are saved now, but current providers do not guarantee strict restriction enforcement.",
                    label = if (routing.vehicleProfile.heightMeters != null) "${"%.1f".format(routing.vehicleProfile.heightMeters)} m" else "Not set",
                    onValueChange = onVehicleHeightChanged
                )
                SliderCard(
                    title = "Vehicle length",
                    value = (routing.vehicleProfile.lengthMeters ?: 0.0).toFloat(),
                    range = 0f..20f,
                    helper = "Useful for RV, truck, and trailer planning when a future provider can honor it directly.",
                    label = if (routing.vehicleProfile.lengthMeters != null) "${"%.1f".format(routing.vehicleProfile.lengthMeters)} m" else "Not set",
                    onValueChange = onVehicleLengthChanged
                )
                SliderCard(
                    title = "Vehicle weight",
                    value = (routing.vehicleProfile.weightTons ?: 0.0).toFloat(),
                    range = 0f..40f,
                    helper = "Useful for heavy vehicles, but still treated as an honest scaffold in this build.",
                    label = if (routing.vehicleProfile.weightTons != null) "${"%.1f".format(routing.vehicleProfile.weightTons)} tons" else "Not set",
                    onValueChange = onVehicleWeightChanged
                )
            } else {
                MessageCard(
                    title = "Commercial vehicle dimensions are postponed",
                    body = "Truck/RV height, length, and weight fields stay in developer builds until provider-backed restriction enforcement is real. The release build avoids pretending those limits are already guaranteed."
                )
            }
            MessageCard(
                title = "Routing limitations",
                body = buildString {
                    append(advisory.summary)
                    if (advisory.limitations.isNotEmpty()) {
                        append("\n")
                        append(advisory.limitations.joinToString(separator = "\n") { "• $it" })
                    }
                }
            )
        }
    }
}

@Composable
private fun ToggleCard(title: String, checked: Boolean, helper: String, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title)
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Text(helper)
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    helper: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title)
            Text(helper)
            options.forEach { (value, label) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = selected == value, onClick = { onSelected(value) })
                    Text(label, modifier = Modifier.padding(top = 12.dp))
                }
            }
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
private fun MessageCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}
