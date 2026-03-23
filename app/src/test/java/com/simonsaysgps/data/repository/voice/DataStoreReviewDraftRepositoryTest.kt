package com.simonsaysgps.data.repository.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import com.simonsaysgps.domain.model.voice.ReviewDraftStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreReviewDraftRepositoryTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `approved review drafts persist across repository instances`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val draftId = "persisted-draft-${System.currentTimeMillis()}"
        val repository = DataStoreReviewDraftRepository(context, moshi)

        repository.startDraft(
            ReviewDraft(
                id = draftId,
                place = PlaceResult(
                    id = "place-1",
                    name = "Scenic Cafe",
                    fullAddress = "1 Main St",
                    coordinate = Coordinate(10.0, 20.0)
                )
            )
        )
        repository.updateRawTranscript("great stop with clean bathrooms")
        repository.applyCleanupSuggestion(ReviewCleanupOption.CLEAN_UP_GRAMMAR, "Great stop with clean bathrooms.")
        repository.approveFinalText("Great stop with clean bathrooms.")

        val reloaded = DataStoreReviewDraftRepository(context, moshi)
        val restored = reloaded.drafts.first().first { it.id == draftId }

        assertThat(restored.status).isEqualTo(ReviewDraftStatus.APPROVED)
        assertThat(restored.finalApprovedText).isEqualTo("Great stop with clean bathrooms.")
        assertThat(restored.place?.name).isEqualTo("Scenic Cafe")
        assertThat(reloaded.activeDraft.first()).isNull()
    }
}
