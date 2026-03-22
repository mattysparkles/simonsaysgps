package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.RepositoryResult

interface GeocodingRepository {
    suspend fun search(query: String): RepositoryResult<List<PlaceResult>>
}
