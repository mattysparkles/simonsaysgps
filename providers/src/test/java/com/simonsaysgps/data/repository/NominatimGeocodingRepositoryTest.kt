package com.simonsaysgps.data.repository

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.data.remote.NominatimApi
import com.simonsaysgps.data.remote.NominatimPlaceDto
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.NetworkFailureType
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.repository.CachedValue
import com.simonsaysgps.domain.repository.SearchCacheStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.UnknownHostException

class NominatimGeocodingRepositoryTest {
    @Test
    fun `uses fresh cached results for repeated queries`() = runTest {
        val cache = FakeSearchCacheStore()
        val expected = listOf(placeResult())
        cache.write("coffee", expected, NOW)
        val api = object : NominatimApi {
            override suspend fun search(query: String, format: String, limit: Int, addressDetails: Int): List<NominatimPlaceDto> {
                error("network should not be called when cache is fresh")
            }
        }

        val repo = NominatimGeocodingRepository(api, cache) { NOW }
        val result = repo.search("coffee")

        assertThat(result).isInstanceOf(RepositoryResult.Success::class.java)
        val success = result as RepositoryResult.Success
        assertThat(success.source).isEqualTo(FetchSource.CACHE)
        assertThat(success.value).isEqualTo(expected)
    }

    @Test
    fun `falls back to cached results when network is offline`() = runTest {
        val cache = FakeSearchCacheStore().apply { write("coffee", listOf(placeResult()), NOW - STALE_DELTA) }
        val api = object : NominatimApi {
            override suspend fun search(query: String, format: String, limit: Int, addressDetails: Int): List<NominatimPlaceDto> {
                throw UnknownHostException("offline")
            }
        }

        val repo = NominatimGeocodingRepository(api, cache) { NOW }
        val result = repo.search("coffee") as RepositoryResult.Success

        assertThat(result.source).isEqualTo(FetchSource.CACHE)
        assertThat(result.fallbackFailure?.type).isEqualTo(NetworkFailureType.NO_NETWORK)
        assertThat(result.value).hasSize(1)
    }

    private class FakeSearchCacheStore : SearchCacheStore {
        private val cache = mutableMapOf<String, CachedValue<List<PlaceResult>>>()

        override suspend fun read(query: String): CachedValue<List<PlaceResult>>? = cache[query]

        override suspend fun write(query: String, results: List<PlaceResult>, timestampMillis: Long) {
            cache[query] = CachedValue(results, timestampMillis)
        }
    }

    private fun placeResult() = PlaceResult(
        id = "1",
        name = "Coffee Shop",
        fullAddress = "123 Roast St",
        coordinate = com.simonsaysgps.domain.model.Coordinate(1.0, 2.0)
    )

    private companion object {
        const val NOW = 1_000_000L
        const val STALE_DELTA = 20 * 60 * 1000L
    }
}
