package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.VisitHistoryEntry
import kotlinx.coroutines.flow.Flow

interface VisitHistoryRepository {
    val visitHistory: Flow<List<VisitHistoryEntry>>

    suspend fun record(entry: VisitHistoryEntry)

    suspend fun remove(placeId: String)

    suspend fun clear()
}
