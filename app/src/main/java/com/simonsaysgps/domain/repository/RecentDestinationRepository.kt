package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.PlaceResult
import kotlinx.coroutines.flow.Flow

interface RecentDestinationRepository {
    val recentDestinations: Flow<List<PlaceResult>>

    suspend fun save(place: PlaceResult)

    suspend fun remove(placeId: String)

    suspend fun clear()
}
