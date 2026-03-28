package com.simonsaysgps.ui.screen.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.ui.model.explore.PlaceDetailStatus
import com.simonsaysgps.ui.model.explore.PlaceDetailUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun PlaceDetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onPreviewOnMap: () -> Unit,
    onStartNavigation: () -> Unit,
    onSeeReviews: () -> Unit,
    onLeaveReview: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    PlaceDetailScreenContent(
        state = state.placeDetail,
        onBack = onBack,
        onPreviewOnMap = onPreviewOnMap,
        onStartNavigation = onStartNavigation,
        onSave = viewModel::toggleSavedPlaceDetail,
        onSeeReviews = onSeeReviews,
        onLeaveReview = {
            viewModel.startLeaveReview()
            onLeaveReview()
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreenContent(
    state: PlaceDetailUiState,
    onBack: () -> Unit,
    onPreviewOnMap: () -> Unit,
    onStartNavigation: () -> Unit,
    onSave: () -> Unit,
    onSeeReviews: () -> Unit,
    onLeaveReview: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.title.isBlank()) "Place detail" else state.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                when (state.status) {
                    PlaceDetailStatus.LOADING -> LoadingCard()
                    PlaceDetailStatus.ERROR -> MessageCard("Place detail unavailable", state.errorMessage ?: "Unknown error")
                    PlaceDetailStatus.EMPTY -> MessageCard("Nothing selected", state.helperMessage ?: "Open a place from Explore first.")
                    else -> Unit
                }
            }
            if (state.status == PlaceDetailStatus.PARTIAL || state.status == PlaceDetailStatus.READY) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.title, style = MaterialTheme.typography.headlineSmall)
                            Text(state.subtitle, style = MaterialTheme.typography.titleMedium)
                            Text(state.address)
                            Text(state.statusLine)
                            Text(state.distanceLine)
                            Text(if (state.isSaved) "Saved place" else "Not saved yet")
                            state.internalRatingSummary?.let { Text("Internal reviews: $it") }
                            state.externalRatingSummary?.let { Text("Provider summaries: $it") }
                            Text("Why this was chosen: ${state.whyChosen}")
                            state.savedSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            state.helperMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            state.phoneNumber?.let { Text("Phone: $it") }
                            state.websiteUrl?.let { Text("Website: $it") }
                            state.hoursSummary?.let { Text("Hours / timing: $it") }
                            if (state.tagLabels.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.tagLabels.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                                }
                            }
                            if (state.eventSnippets.isNotEmpty()) {
                                Text("Event snippets", style = MaterialTheme.typography.titleMedium)
                                state.eventSnippets.take(3).forEach { Text("• $it") }
                            }
                            if (state.sourceLabels.isNotEmpty()) {
                                Text("Sources: ${state.sourceLabels.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onStartNavigation) {
                            Icon(Icons.Default.Navigation, contentDescription = null)
                            Text("Start Navigation")
                        }
                        OutlinedButton(onClick = onPreviewOnMap) {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Text("Preview on Map")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSave) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Text(if (state.isSaved) "Unsave" else "Save")
                        }
                        OutlinedButton(onClick = onSeeReviews) {
                            Icon(Icons.Default.Star, contentDescription = null)
                            Text("See Reviews")
                        }
                        OutlinedButton(onClick = onLeaveReview) {
                            Icon(Icons.Default.RateReview, contentDescription = null)
                            Text("Leave Review")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Loading place detail…")
        }
    }
}

@Composable
private fun MessageCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}
