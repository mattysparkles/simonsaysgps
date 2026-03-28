package com.simonsaysgps.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.repository.CachedValue
import com.simonsaysgps.domain.repository.RouteCacheStore
import com.simonsaysgps.domain.repository.SearchCacheStore
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.networkCacheDataStore by preferencesDataStore(name = "simonsays_network_cache")

@Singleton
class DataStoreSearchCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : SearchCacheStore {
    private val adapter: JsonAdapter<List<StoredSearchCacheEntry>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredSearchCacheEntry::class.java)
    )

    override suspend fun read(query: String): CachedValue<List<PlaceResult>>? {
        val normalizedQuery = query.trim().lowercase()
        val prefs = context.networkCacheDataStore.data.first()
        val entry = SearchCacheStorage.decode(prefs[SEARCH_CACHE_KEY], adapter)
            .firstOrNull { it.query == normalizedQuery }
            ?: return null
        return CachedValue(entry.results.map(StoredRecentDestination::toPlaceResult), entry.timestampMillis)
    }

    override suspend fun write(query: String, results: List<PlaceResult>, timestampMillis: Long) {
        val normalizedQuery = query.trim().lowercase()
        context.networkCacheDataStore.edit { prefs ->
            val existing = SearchCacheStorage.decode(prefs[SEARCH_CACHE_KEY], adapter)
                .filterNot { it.query == normalizedQuery }
            val updated = listOf(
                StoredSearchCacheEntry(
                    query = normalizedQuery,
                    timestampMillis = timestampMillis,
                    results = results.map(StoredRecentDestination::fromPlaceResult)
                )
            ) + existing
            prefs[SEARCH_CACHE_KEY] = SearchCacheStorage.encode(updated.take(MAX_SEARCH_ENTRIES), adapter)
        }
    }

    private companion object {
        val SEARCH_CACHE_KEY = stringPreferencesKey("search_cache_entries")
        const val MAX_SEARCH_ENTRIES = 10
    }
}

@Singleton
class DataStoreRouteCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : RouteCacheStore {
    private val adapter: JsonAdapter<List<StoredRouteCacheEntry>> = RouteCacheStorage.adapter(moshi)

    override suspend fun read(originKey: String, destinationKey: String): CachedValue<Route>? {
        val prefs = context.networkCacheDataStore.data.first()
        val entry = RouteCacheStorage.decode(prefs[ROUTE_CACHE_KEY], adapter)
            .firstOrNull { it.originKey == originKey && it.destinationKey == destinationKey }
            ?: return null
        return CachedValue(entry.route.toRoute(), entry.timestampMillis)
    }

    override suspend fun write(originKey: String, destinationKey: String, route: Route, timestampMillis: Long) {
        context.networkCacheDataStore.edit { prefs ->
            val existing = RouteCacheStorage.decode(prefs[ROUTE_CACHE_KEY], adapter)
                .filterNot { it.originKey == originKey && it.destinationKey == destinationKey }
            val updated = listOf(
                StoredRouteCacheEntry(
                    originKey = originKey,
                    destinationKey = destinationKey,
                    timestampMillis = timestampMillis,
                    route = StoredRoute.fromRoute(route)
                )
            ) + existing
            prefs[ROUTE_CACHE_KEY] = RouteCacheStorage.encode(updated.take(MAX_ROUTE_ENTRIES), adapter)
        }
    }

    private companion object {
        val ROUTE_CACHE_KEY = stringPreferencesKey("route_cache_entries")
        const val MAX_ROUTE_ENTRIES = 3
    }
}

internal object SearchCacheStorage {
    fun decode(rawValue: String?, adapter: JsonAdapter<List<StoredSearchCacheEntry>>): List<StoredSearchCacheEntry> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(rawValue).orEmpty() }.getOrDefault(emptyList())
    }

    fun encode(entries: List<StoredSearchCacheEntry>, adapter: JsonAdapter<List<StoredSearchCacheEntry>>): String = adapter.toJson(entries)
}

internal data class StoredSearchCacheEntry(
    val query: String,
    val timestampMillis: Long,
    val results: List<StoredRecentDestination>
)

internal object RouteCacheStorage {
    fun adapter(moshi: Moshi): JsonAdapter<List<StoredRouteCacheEntry>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredRouteCacheEntry::class.java)
    )

    fun decode(rawValue: String?, adapter: JsonAdapter<List<StoredRouteCacheEntry>>): List<StoredRouteCacheEntry> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(rawValue).orEmpty() }.getOrDefault(emptyList())
    }

    fun encode(entries: List<StoredRouteCacheEntry>, adapter: JsonAdapter<List<StoredRouteCacheEntry>>): String = adapter.toJson(entries)
}

internal data class StoredRouteCacheEntry(
    val originKey: String,
    val destinationKey: String,
    val timestampMillis: Long,
    val route: StoredRoute
)
