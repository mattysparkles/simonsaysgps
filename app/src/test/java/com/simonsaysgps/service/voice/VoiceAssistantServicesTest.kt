package com.simonsaysgps.service.voice

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.data.repository.voice.InMemoryCrowdReportRepository
import com.simonsaysgps.data.repository.voice.InMemoryReviewDraftRepository
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.voice.CrowdReportType
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.SoundtrackIntent
import com.simonsaysgps.domain.model.voice.VoiceContext
import com.simonsaysgps.domain.model.voice.VoiceIntent
import com.simonsaysgps.domain.service.voice.VoiceDispatchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoiceAssistantServicesTest {
    private val parser = RuleBasedVoiceIntentParser()

    @Test
    fun `transcript parser maps commands to supported intents`() {
        assertThat(parser.parse("Simon, find coffee on my way")).isEqualTo(VoiceIntent.SearchOnRoute("coffee"))
        assertThat(parser.parse("Simon, take me somewhere fun nearby")).isEqualTo(VoiceIntent.Explore(com.simonsaysgps.domain.model.explore.ExploreCategory.FUN))
        assertThat(parser.parse("Simon, report police")).isEqualTo(VoiceIntent.CrowdReport(CrowdReportType.SPEED_TRAP, note = "police", confidence = 0.9f))
        assertThat(parser.parse("Simon, make me a beach playlist")).isEqualTo(VoiceIntent.Soundtrack("beach"))
    }

    @Test
    fun `report confirmation flow stages and submits normalized report`() = runTest {
        val reports = InMemoryCrowdReportRepository()
        val drafts = InMemoryReviewDraftRepository()
        val dispatcher = DefaultVoiceActionDispatcher(reports, drafts, StubProseCleanupService(), DemoMusicIntentProvider())

        val staged = dispatcher.dispatch(
            VoiceIntent.CrowdReport(CrowdReportType.TRAFFIC, "heavy slowdown", 0.876f),
            VoiceContext(currentLocation = Coordinate(1.0, 2.0))
        )
        assertThat(staged).isInstanceOf(VoiceDispatchResult.ReportStaged::class.java)
        assertThat(reports.pendingReport.first()?.confidence).isEqualTo(0.88f)
        assertThat(reports.pendingReport.first()?.location).isEqualTo(Coordinate(1.0, 2.0))

        val submitted = dispatcher.dispatch(VoiceIntent.Confirmation(true), VoiceContext())
        assertThat(submitted).isInstanceOf(VoiceDispatchResult.ReportSubmitted::class.java)
        assertThat(reports.reports.first()).hasSize(1)
        assertThat(reports.reports.first().first().userConfirmed).isTrue()
    }

    @Test
    fun `review draft state transitions and cleanup routing stay distinct`() = runTest {
        val reports = InMemoryCrowdReportRepository()
        val drafts = InMemoryReviewDraftRepository()
        val dispatcher = DefaultVoiceActionDispatcher(reports, drafts, StubProseCleanupService(), DemoMusicIntentProvider())
        val place = PlaceResult("1", "Cafe", "1 Main St", Coordinate(0.0, 0.0))

        dispatcher.dispatch(VoiceIntent.ReviewCurrentPlace, VoiceContext(selectedPlace = place))
        drafts.updateRawTranscript("great coffee and fast service")
        dispatcher.dispatch(VoiceIntent.ReviewCleanup(ReviewCleanupOption.CLEAN_UP_GRAMMAR), VoiceContext(selectedPlace = place))

        val draft = drafts.activeDraft.first()
        assertThat(draft?.place?.name).isEqualTo("Cafe")
        assertThat(draft?.rawTranscript).isEqualTo("great coffee and fast service")
        assertThat(draft?.cleanedSuggestion).isEqualTo("Great coffee and fast service.")
        drafts.approveFinalText(draft?.cleanedSuggestion.orEmpty())
        assertThat(drafts.activeDraft.first()?.finalApprovedText).isEqualTo("Great coffee and fast service.")
    }

    @Test
    fun `soundtrack provider stays provider agnostic`() = runTest {
        val provider = DemoMusicIntentProvider()
        val result = provider.handle(SoundtrackIntent(vibe = "spooky road trip", providerHint = "Demo"))

        assertThat(result.providerName).isEqualTo("Demo")
        assertThat(result.message).contains("spooky road trip")
        assertThat(result.launched).isFalse()
    }

    @Test
    fun `speech capture manager exposes transcript state`() {
        val manager = StubSpeechCaptureManager()
        manager.startListening()
        manager.submitTranscript("Simon, report pothole")

        assertThat(manager.captureState.value.toString()).contains("TranscriptAvailable")
    }
}
