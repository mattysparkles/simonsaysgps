package com.simonsaysgps.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.repository.RecentDestinationRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentDestinationDataStore by preferencesDataStore(name = "simonsays_recent_destinations")

@Singleton
class DataStoreRecentDestinationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : RecentDestinationRepository {
    private val adapter: JsonAdapter<List<StoredRecentDestination>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredRecentDestination::class.java)
    )

    override val recentDestinations: Flow<List<PlaceResult>> = context.recentDestinationDataStore.data.map { prefs ->
        RecentDestinationStorage.decode(prefs[RECENT_DESTINATIONS], adapter)
    }

    override suspend fun save(place: PlaceResult) {
        context.recentDestinationDataStore.edit { prefs ->
            val existing = RecentDestinationStorage.decode(prefs[RECENT_DESTINATIONS], adapter)
            val updated = listOf(place) + existing.filterNot { it.id == place.id }
            prefs[RECENT_DESTINATIONS] = RecentDestinationStorage.encode(updated.take(MAX_RECENT_DESTINATIONS), adapter)
        }
    }

    override suspend fun remove(placeId: String) {
        context.recentDestinationDataStore.edit { prefs ->
            val updated = RecentDestinationStorage.decode(prefs[RECENT_DESTINATIONS], adapter)
                .filterNot { it.id == placeId }
            prefs[RECENT_DESTINATIONS] = RecentDestinationStorage.encode(updated, adapter)
        }
    }

    override suspend fun clear() {
        context.recentDestinationDataStore.edit { prefs ->
            prefs.remove(RECENT_DESTINATIONS)
        }
    }

    private companion object {
        private val RECENT_DESTINATIONS = stringPreferencesKey("recent_destinations")
        private const val MAX_RECENT_DESTINATIONS = 6
    }
}

internal object RecentDestinationStorage {
    fun encode(
        places: List<PlaceResult>,
        adapter: JsonAdapter<List<StoredRecentDestination>>
    ): String = adapter.toJson(places.map(StoredRecentDestination::fromPlaceResult))

    fun decode(
        rawValue: String?,
        adapter: JsonAdapter<List<StoredRecentDestination>>
    ): List<PlaceResult> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(rawValue)
                ?.map(StoredRecentDestination::toPlaceResult)
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}

internal data class StoredRecentDestination(
    val id: String,
    val name: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double
) {
    fun toPlaceResult() = PlaceResult(
        id = id,
        name = name,
        fullAddress = fullAddress,
        coordinate = Coordinate(latitude = latitude, longitude = longitude)
    )

    companion object {
        fun fromPlaceResult(place: PlaceResult) = StoredRecentDestination(
            id = place.id,
            name = place.name,
            fullAddress = place.fullAddress,
            latitude = place.coordinate.latitude,
            longitude = place.coordinate.longitude
        )
    }
}
