package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.Route

data class CachedValue<T>(
    val value: T,
    val timestampMillis: Long
)

interface SearchCacheStore {
    suspend fun read(query: String): CachedValue<List<PlaceResult>>?
    suspend fun write(query: String, results: List<PlaceResult>, timestampMillis: Long)
}

interface RouteCacheStore {
    suspend fun read(originKey: String, destinationKey: String): CachedValue<Route>?
    suspend fun write(originKey: String, destinationKey: String, route: Route, timestampMillis: Long)
}
