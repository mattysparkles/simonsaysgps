package com.simonsaysgps.ui.screen.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.simonsaysgps.ui.model.explore.PlaceDetailUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaceReviewsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLeaveReview: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    PlaceReviewsScreenContent(
        state = state.placeDetail,
        onBack = onBack,
        onLeaveReview = {
            viewModel.startLeaveReview()
            onLeaveReview()
        },
        onRemoveOwnReview = viewModel::removeOwnReview,
        onReportReview = viewModel::reportReviewPlaceholder
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceReviewsScreenContent(
    state: PlaceDetailUiState,
    onBack: () -> Unit,
    onLeaveReview: () -> Unit,
    onRemoveOwnReview: (String) -> Unit,
    onReportReview: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.title.isBlank()) "Reviews" else "Reviews · ${state.title}") },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Internal reviews first", style = MaterialTheme.typography.titleMedium)
                        Text(state.internalRatingSummary ?: "No internal reviews yet. Leave the first review for this place.")
                        OutlinedButton(onClick = onLeaveReview) { Text(if (state.internalReviews.isEmpty()) "Leave Review" else "Add / Edit Review") }
                    }
                }
            }
            items(state.internalReviews, key = { it.internalReviewId }) { review ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${review.rating}★ · ${review.authorDisplayName}", style = MaterialTheme.typography.titleMedium)
                        Text(formatDate(review.updatedAtEpochMillis), style = MaterialTheme.typography.bodySmall)
                        Text(review.reviewText)
                        if (review.tags.isNotEmpty()) {
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                review.tags.forEach { AssistChip(onClick = {}, label = { Text(it.label) }) }
                            }
                        }
                        OutlinedButton(onClick = { onReportReview(review.authorDisplayName) }) { Text("Report review (hook)") }
                        if (review.authorDisplayName == "Local driver") {
                            OutlinedButton(onClick = { onRemoveOwnReview(review.internalReviewId) }) { Text("Delete local review") }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("External provider summaries", style = MaterialTheme.typography.titleMedium)
                        Text("Shown separately so internal community reviews are never blended into third-party counts.")
                    }
                }
            }
            items(state.externalReviewSummaries, key = { it.provider }) { block ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(block.providerLabel, style = MaterialTheme.typography.titleMedium)
                        Text(buildString {
                            block.averageRating?.let { append("${"%.1f".format(it)}★ · ") }
                            append("${block.reviewCount} reviews")
                        })
                        Text(block.summary ?: "Provider only exposes an attributed summary block in this phase.")
                        Text("Source: ${block.attribution.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))
