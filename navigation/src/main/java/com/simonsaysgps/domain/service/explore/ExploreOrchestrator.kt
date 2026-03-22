package com.simonsaysgps.domain.service.explore

import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreResponse
import com.simonsaysgps.domain.model.explore.ExploreSuggestionCount
import com.simonsaysgps.domain.repository.explore.ExploreRepository

interface ExploreOrchestrator {
    suspend fun explore(query: ExploreQuery): ExploreResponse
}

class DefaultExploreOrchestrator(
    private val repository: ExploreRepository,
    private val rankingEngine: ExploreRankingEngine = ExploreRankingEngine()
) : ExploreOrchestrator {
    override suspend fun explore(query: ExploreQuery): ExploreResponse {
        val snapshot = repository.fetch(query)
        val ranked = rankingEngine.rank(query, snapshot.candidates)
        val limited = when (query.settings.suggestionCount) {
            ExploreSuggestionCount.AUTO_PICK -> ranked.take(1)
            ExploreSuggestionCount.THREE_CHOICES -> ranked.take(3)
        }
        return ExploreResponse(
            results = limited,
            providerStatuses = snapshot.providerStatuses,
            autoPicked = query.settings.suggestionCount == ExploreSuggestionCount.AUTO_PICK && limited.isNotEmpty(),
            totalCandidatesConsidered = snapshot.candidates.size
        )
    }
}
