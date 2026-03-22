package com.simonsaysgps.ui.screen.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreResult
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun ExploreResultsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onPreviewOnMap: () -> Unit,
    onStartNavigation: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ExploreResultsScreenContent(
        category = state.explore.selectedCategory,
        loading = state.explore.loading,
        results = state.explore.results,
        providerStatuses = state.explore.providerStatuses,
        infoMessage = state.explore.infoMessage,
        errorMessage = state.explore.errorMessage,
        actionMessage = state.explore.actionMessage,
        autoPicked = state.explore.autoPicked,
        onBack = onBack,
        onPreviewOnMap = { result ->
            viewModel.previewExploreResult(result)
            onPreviewOnMap()
        },
        onStartNavigation = { result ->
            viewModel.previewExploreResult(result)
            viewModel.requestRoute()
            onStartNavigation()
        },
        onSave = viewModel::saveExploreResult,
        onSeeReviews = { result -> viewModel.noteExploreAction("Review detail stub: ${result.candidate.reviewSummary?.summary ?: "No review summary available yet."}") },
        onLeaveReview = { result -> viewModel.noteExploreAction("Leave-review flow stub for ${result.candidate.name}. Hook this to your review composer later.") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreResultsScreenContent(
    category: ExploreCategory?,
    loading: Boolean,
    results: List<ExploreResult>,
    providerStatuses: List<ExploreProviderStatus>,
    infoMessage: String?,
    errorMessage: String?,
    actionMessage: String?,
    autoPicked: Boolean,
    onBack: () -> Unit,
    onPreviewOnMap: (ExploreResult) -> Unit,
    onStartNavigation: (ExploreResult) -> Unit,
    onSave: (ExploreResult) -> Unit,
    onSeeReviews: (ExploreResult) -> Unit,
    onLeaveReview: (ExploreResult) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.displayName ?: "Explore Results") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
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
            if (loading) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Ranking nearby Explore candidates…")
                    }
                }
            }
            if (autoPicked) {
                AssistChip(onClick = {}, label = { Text("1 auto-pick mode") })
            }
            infoMessage?.let { MessageCard(title = "Explore note", body = it) }
            errorMessage?.let { MessageCard(title = "Explore unavailable", body = it) }
            actionMessage?.let { MessageCard(title = "Quick action", body = it) }
            if (providerStatuses.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Provider status", style = MaterialTheme.typography.titleMedium)
                        providerStatuses.forEach { status ->
                            Text("• ${status.provider}: ${if (status.available) "ready" else "fallback"} — ${status.detail}")
                        }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results, key = { it.candidate.id }) { result ->
                    ExploreResultCard(
                        result = result,
                        onPreviewOnMap = { onPreviewOnMap(result) },
                        onStartNavigation = { onStartNavigation(result) },
                        onSave = { onSave(result) },
                        onSeeReviews = { onSeeReviews(result) },
                        onLeaveReview = { onLeaveReview(result) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreResultCard(
    result: ExploreResult,
    onPreviewOnMap: () -> Unit,
    onStartNavigation: () -> Unit,
    onSave: () -> Unit,
    onSeeReviews: () -> Unit,
    onLeaveReview: () -> Unit
) {
    val candidate = result.candidate
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleLarge)
            Text(candidate.typeLabel, style = MaterialTheme.typography.labelLarge)
            Text(candidate.address)
            Text(candidate.eventInfo?.summary ?: if (candidate.openNow == true) "Open now" else if (candidate.openNow == false) "Currently closed" else "Hours unknown")
            Text(result.offRouteDistanceMeters?.let { "${formatMiles(it)} miles off route" } ?: "${formatMiles(result.distanceMeters)} miles away")
            Text(candidate.reviewSummary?.let { "${"%.1f".format(it.averageRating)}★ · ${it.totalCount} ratings" } ?: "Ratings unavailable")
            Text(result.primaryWhyChosen, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreviewOnMap) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Text("Preview on map")
                }
                OutlinedButton(onClick = onStartNavigation) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Text("Start navigation")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Save")
                }
                OutlinedButton(onClick = onSeeReviews) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text("See reviews")
                }
                OutlinedButton(onClick = onLeaveReview) {
                    Icon(Icons.Default.RateReview, contentDescription = null)
                    Text("Leave review")
                }
            }
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

private fun formatMiles(distanceMeters: Double): String = "%.1f".format(distanceMeters / 1609.34)
