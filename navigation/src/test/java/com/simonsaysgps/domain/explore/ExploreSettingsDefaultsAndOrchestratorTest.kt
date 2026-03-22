package com.simonsaysgps.domain.explore

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreCandidate
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreFacet
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreRepositorySnapshot
import com.simonsaysgps.domain.model.explore.ExploreSettings
import com.simonsaysgps.domain.model.explore.ExploreSuggestionCount
import com.simonsaysgps.domain.repository.explore.ExploreRepository
import com.simonsaysgps.domain.service.explore.DefaultExploreOrchestrator
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExploreSettingsDefaultsAndOrchestratorTest {
    @Test
    fun `explore settings defaults match product expectations`() {
        val settings = ExploreSettings()

        assertThat(settings.defaultRadiusMiles).isEqualTo(10)
        assertThat(settings.requireOpenNowByDefault).isTrue()
        assertThat(settings.suggestionCount).isEqualTo(ExploreSuggestionCount.THREE_CHOICES)
        assertThat(settings.allowRouteDetoursWhileNavigating).isTrue()
        assertThat(settings.useEventDataWhenAvailable).isTrue()
        assertThat(settings.useInternalReviewsFirst).isTrue()
        assertThat(settings.includeThirdPartyReviewSummariesWhenAvailable).isTrue()
        assertThat(settings.closeToHomeRadiusMiles).isEqualTo(8)
        assertThat(settings.visitHistoryEnabled).isTrue()
        assertThat(settings.visitHistoryRetentionDays).isEqualTo(180)
        assertThat(settings.avoidAlcoholFocusedVenues).isTrue()
        assertThat(settings.avoidAdultOrientedVenues).isTrue()
    }

    @Test
    fun `auto pick returns one result while three choices returns up to three`() = runTest {
        val repository = FakeExploreRepository()
        val orchestrator = DefaultExploreOrchestrator(repository)

        val autoPickResponse = orchestrator.explore(
            ExploreQuery(
                category = ExploreCategory.OPEN_NOW,
                userLocation = Coordinate(40.7484, -73.9857),
                settings = ExploreSettings(suggestionCount = ExploreSuggestionCount.AUTO_PICK)
            )
        )
        val threeChoiceResponse = orchestrator.explore(
            ExploreQuery(
                category = ExploreCategory.OPEN_NOW,
                userLocation = Coordinate(40.7484, -73.9857),
                settings = ExploreSettings(suggestionCount = ExploreSuggestionCount.THREE_CHOICES)
            )
        )

        assertThat(autoPickResponse.autoPicked).isTrue()
        assertThat(autoPickResponse.results).hasSize(1)
        assertThat(threeChoiceResponse.autoPicked).isFalse()
        assertThat(threeChoiceResponse.results).hasSize(3)
    }

    @Test
    fun `provider fallback keeps explore usable when one provider is unavailable`() = runTest {
        val repository = object : ExploreRepository {
            override suspend fun fetch(query: ExploreQuery): ExploreRepositorySnapshot = ExploreRepositorySnapshot(
                candidates = listOf(candidate("park")),
                providerStatuses = listOf(
                    ExploreProviderStatus("place-data", true, "Demo nearby catalog active."),
                    ExploreProviderStatus("event-data", false, "No event provider available in this environment.")
                )
            )
        }
        val orchestrator = DefaultExploreOrchestrator(repository)

        val response = orchestrator.explore(ExploreQuery(ExploreCategory.OUTDOORS, Coordinate(40.7484, -73.9857)))

        assertThat(response.results).isNotEmpty()
        assertThat(response.providerStatuses.any { !it.available }).isTrue()
    }

    private class FakeExploreRepository : ExploreRepository {
        override suspend fun fetch(query: ExploreQuery): ExploreRepositorySnapshot = ExploreRepositorySnapshot(
            candidates = listOf(candidate("one"), candidate("two"), candidate("three"), candidate("four")),
            providerStatuses = listOf(ExploreProviderStatus("place-data", true, "OK"))
        )
    }

    private fun candidate(id: String) = ExploreCandidate(
        id = id,
        name = "Candidate $id",
        typeLabel = "Cafe",
        address = "123 Test St",
        coordinate = Coordinate(40.7484, -73.9857),
        facets = setOf(ExploreFacet.FOOD),
        openNow = true,
        sourceConfidence = 0.7f,
        accessible = true,
        kidFriendly = true,
        quietScore = 0.6f
    )
}
