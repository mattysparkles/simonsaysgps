package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.PlaceResult

interface GeocodingRepository {
    suspend fun search(query: String): Result<List<PlaceResult>>
}
