package com.simonsaysgps.data.repository

import com.simonsaysgps.data.remote.NominatimApi
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.repository.GeocodingRepository
import com.simonsaysgps.domain.repository.SearchCacheStore
import javax.inject.Inject

class NominatimGeocodingRepository @Inject constructor(
    private val api: NominatimApi,
    private val searchCacheStore: SearchCacheStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : GeocodingRepository {
    override suspend fun search(query: String, proximity: Coordinate?): RepositoryResult<List<PlaceResult>> {
        if (query.isBlank()) return RepositoryResult.Success(emptyList(), FetchSource.NETWORK)

        val normalizedQuery = query.trim().lowercase()
        val cacheKey = cacheKey(normalizedQuery, proximity)
        val cached = searchCacheStore.read(cacheKey)
        if (cached != null && clock() - cached.timestampMillis <= SEARCH_CACHE_TTL_MS) {
            return RepositoryResult.Success(cached.value, FetchSource.CACHE)
        }

        return runCatching {
            api.search(
                query = query,
                viewbox = proximity?.let(::proximityViewbox)
            ).map {
                PlaceResult(
                    id = it.place_id.toString(),
                    name = it.name ?: it.display_name.substringBefore(','),
                    fullAddress = it.display_name,
                    coordinate = Coordinate(it.lat.toDouble(), it.lon.toDouble())
                )
            }
        }.fold(
            onSuccess = { results ->
                searchCacheStore.write(cacheKey, results, clock())
                RepositoryResult.Success(results, FetchSource.NETWORK)
            },
            onFailure = { error ->
                val cachedFallback = cached ?: searchCacheStore.read(cacheKey)
                if (cachedFallback != null) {
                    RepositoryResult.Success(
                        value = cachedFallback.value,
                        source = FetchSource.CACHE,
                        fallbackFailure = NetworkFailureClassifier.classify(error)
                    )
                } else {
                    RepositoryResult.Failure(NetworkFailureClassifier.classify(error))
                }
            }
        )
    }

    private companion object {
        const val SEARCH_CACHE_TTL_MS = 15 * 60 * 1000L

        fun cacheKey(query: String, proximity: Coordinate?): String {
            if (proximity == null) return query
            val latBucket = "%.2f".format(proximity.latitude)
            val lonBucket = "%.2f".format(proximity.longitude)
            return "$query@$latBucket,$lonBucket"
        }

        fun proximityViewbox(proximity: Coordinate): String {
            val latitudeDelta = 0.12
            val longitudeDelta = latitudeDelta / kotlin.math.cos(Math.toRadians(proximity.latitude)).coerceAtLeast(0.2)
            val left = proximity.longitude - longitudeDelta
            val right = proximity.longitude + longitudeDelta
            val top = proximity.latitude + latitudeDelta
            val bottom = proximity.latitude - latitudeDelta
            return "$left,$top,$right,$bottom"
        }
    }
}
