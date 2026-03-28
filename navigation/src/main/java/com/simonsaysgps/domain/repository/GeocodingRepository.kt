package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.RepositoryResult

interface GeocodingRepository {
    suspend fun search(query: String, proximity: Coordinate? = null): RepositoryResult<List<PlaceResult>>
}
