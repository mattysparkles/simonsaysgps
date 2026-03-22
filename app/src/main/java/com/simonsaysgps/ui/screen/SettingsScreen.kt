package com.simonsaysgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.PromptFrequency
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            ToggleCard("Voice prompts", settings.voiceEnabled) { value -> viewModel.updateSettings { it.copy(voiceEnabled = value) } }
            ToggleCard("Debug overlay", settings.debugMode) { value -> viewModel.updateSettings { it.copy(debugMode = value) } }
            ToggleCard("Demo mode", settings.demoMode) { value -> viewModel.updateSettings { it.copy(demoMode = value) } }
            ChoiceCard(
                title = "Game mode",
                options = listOf(GameMode.BASIC.name to "Basic", GameMode.MISCHIEF.name to "Mischief"),
                selected = settings.gameMode.name,
                onSelected = { selected -> viewModel.updateSettings { current -> current.copy(gameMode = GameMode.valueOf(selected)) } }
            )
            ChoiceCard(
                title = "Prompt frequency",
                options = PromptFrequency.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selected = settings.promptFrequency.name,
                onSelected = { selected -> viewModel.updateSettings { it.copy(promptFrequency = PromptFrequency.valueOf(selected)) } }
            )
            ChoiceCard(
                title = "Units",
                options = DistanceUnit.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selected = settings.distanceUnit.name,
                onSelected = { selected -> viewModel.updateSettings { it.copy(distanceUnit = DistanceUnit.valueOf(selected)) } }
            )
        }
    }
}

@Composable
private fun ToggleCard(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title)
            options.forEach { (value, label) ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = selected == value, onClick = { onSelected(value) })
                    Text(label, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
