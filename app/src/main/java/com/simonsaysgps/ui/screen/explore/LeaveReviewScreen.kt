package com.simonsaysgps.ui.screen.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.simonsaysgps.ui.model.explore.ReviewComposeUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun LeaveReviewScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LeaveReviewScreenContent(
        state = state.reviewCompose,
        onBack = onBack,
        onRatingSelected = viewModel::updateReviewRating,
        onTextChanged = viewModel::updateReviewText,
        onToggleTag = viewModel::toggleReviewTag,
        onSubmit = viewModel::submitReview,
        onDismissMessage = viewModel::dismissReviewComposeMessage
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeaveReviewScreenContent(
    state: ReviewComposeUiState,
    onBack: () -> Unit,
    onRatingSelected: (Int) -> Unit,
    onTextChanged: (String) -> Unit,
    onToggleTag: (PlaceReviewTag) -> Unit,
    onSubmit: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.placeName.isBlank()) "Leave review" else "Review · ${state.placeName}") },
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
            Text(if (state.isEditing) "Update your local review" else "Create a local internal review")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    FilterChip(
                        selected = state.rating == star,
                        onClick = { onRatingSelected(star) },
                        label = { Text("$star★") }
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.reviewText,
                onValueChange = onTextChanged,
                minLines = 5,
                label = { Text("Review") },
                supportingText = { Text("Describe what worked, what to expect, and who this stop is good for.") }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceReviewTag.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in state.selectedTags,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag.label) }
                    )
                }
            }
            state.helperMessage?.let { AssistChip(onClick = onDismissMessage, label = { Text(it) }) }
            state.validationError?.let { Text(it) }
            state.successMessage?.let { Text(it) }
            OutlinedButton(onClick = onSubmit) { Text(if (state.isEditing) "Save Changes" else "Submit Review") }
        }
    }
}
