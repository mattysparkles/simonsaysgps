package com.simonsaysgps.ui.screen.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.voice.CrowdReportType
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.ui.viewmodel.AppViewModel
import com.simonsaysgps.ui.viewmodel.VoiceAssistantUiState

@Composable
fun VoiceAssistantScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    requestMicrophonePermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    VoiceAssistantScreenContent(
        state = state.voiceAssistant,
        voiceEnabled = state.settings.voiceAssistantSettings.enabled,
        voiceConfirmationRequired = state.settings.voiceAssistantSettings.voiceConfirmationRequired,
        aiCleanupEnabled = state.settings.voiceAssistantSettings.aiCleanupOptIn,
        soundtrackEnabled = state.settings.voiceAssistantSettings.soundtrackIntegrationEnabled,
        onBack = onBack,
        onRequestMicrophonePermission = requestMicrophonePermission,
        onTranscriptChanged = viewModel::updateVoiceTranscript,
        onStartListening = viewModel::startVoiceCapture,
        onStopListening = viewModel::stopVoiceCapture,
        onSubmitTranscript = viewModel::submitVoiceTranscript,
        onManualReport = viewModel::stageManualReport,
        onConfirmPending = viewModel::confirmPendingVoiceAction,
        onStartReview = viewModel::startReviewDraft,
        onReviewTranscriptChanged = viewModel::updateReviewDraftTranscript,
        onCleanupSelected = viewModel::applyReviewCleanup,
        onApproveReview = viewModel::approveReviewDraft,
        onClearReview = viewModel::clearReviewDraft
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceAssistantScreenContent(
    state: VoiceAssistantUiState,
    voiceEnabled: Boolean,
    voiceConfirmationRequired: Boolean,
    aiCleanupEnabled: Boolean,
    soundtrackEnabled: Boolean,
    onBack: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onTranscriptChanged: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSubmitTranscript: () -> Unit,
    onManualReport: (CrowdReportType) -> Unit,
    onConfirmPending: (Boolean) -> Unit,
    onStartReview: () -> Unit,
    onReviewTranscriptChanged: (String) -> Unit,
    onCleanupSelected: (ReviewCleanupOption) -> Unit,
    onApproveReview: (String) -> Unit,
    onClearReview: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Voice Assistant") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Driver-friendly voice input")
                    Text("This layer keeps capture, transcript parsing, intent routing, confirmation, and final actions separate so it can grow without replacing the current TTS stack.")
                    if (!state.hasMicrophonePermission) {
                        Button(onClick = onRequestMicrophonePermission, modifier = Modifier.fillMaxWidth()) { Text("Grant microphone") }
                    }
                    Text("Voice assistant enabled: $voiceEnabled")
                    Text("Voice confirmation required: $voiceConfirmationRequired")
                    Text("AI cleanup opt-in: $aiCleanupEnabled")
                    Text("Soundtrack integrations enabled: $soundtrackEnabled")
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Speak or type a command")
                    OutlinedTextField(
                        value = state.draftTranscript,
                        onValueChange = onTranscriptChanged,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("Example: Simon, report speed trap") }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartListening, enabled = state.hasMicrophonePermission) { Text("Start listening") }
                        Button(onClick = onStopListening) { Text("Stop") }
                        Button(onClick = onSubmitTranscript) { Text("Run command") }
                    }
                    Text("Capture state: ${state.captureState}")
                    state.lastTranscript?.let { Text("Last transcript: $it") }
                    state.lastActionMessage?.let { Text("Assistant: $it") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Passenger quick reports")
                    Text("Large tap targets are available for passengers or safe parked use. Voice submissions still require explicit confirmation before send.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CrowdReportType.entries.forEach { type ->
                            Button(onClick = { onManualReport(type) }) { Text(type.label) }
                        }
                    }
                    state.pendingReport?.let { report ->
                        Text("Pending: ${report.type.label} · confidence ${report.confidence}")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfirmPending(true) }) { Text("Confirm report") }
                            Button(onClick = { onConfirmPending(false) }) { Text("Cancel") }
                        }
                    }
                    if (state.submittedReports.isNotEmpty()) {
                        Text("Recent submitted reports")
                        state.submittedReports.take(3).forEach { report ->
                            Text("• ${report.type.label} at ${report.timestampEpochMillis} · confirmed=${report.userConfirmed}")
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Voice review drafting")
                    Button(onClick = onStartReview, modifier = Modifier.fillMaxWidth()) { Text("Start review draft") }
                    state.activeReviewDraft?.let { draft ->
                        OutlinedTextField(
                            value = draft.rawTranscript,
                            onValueChange = onReviewTranscriptChanged,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("Dictated review") }
                        )
                        if (aiCleanupEnabled) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewCleanupOption.entries.forEach { option ->
                                    Button(onClick = { onCleanupSelected(option) }) { Text(option.label) }
                                }
                            }
                        }
                        draft.cleanedSuggestion?.let {
                            Text("Suggested cleanup: $it")
                            Button(onClick = { onApproveReview(it) }) { Text("Approve suggestion") }
                        }
                        draft.finalApprovedText?.let { Text("Approved text: $it") }
                        TextButton(onClick = onClearReview) { Text("Clear review draft") }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Soundtrack scaffolding")
                    Text("Ask for vibes like 'make me a spooky road trip playlist' or 'make me a beach playlist'. This PR stores the intent and returns a demo provider response instead of invoking a proprietary SDK.")
                    RowToggle(label = "Ready for future provider deep links", checked = soundtrackEnabled)
                }
            }
        }
    }
}

@Composable
private fun RowToggle(label: String, checked: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = {}, enabled = false)
        }
    }
}
