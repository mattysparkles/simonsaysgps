package com.simonsaysgps.data.repository.explore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.model.explore.SavedPlaceRecord
import com.simonsaysgps.domain.repository.explore.SavedPlaceRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.savedPlaceDataStore by preferencesDataStore(name = "simonsays_saved_places")

@Singleton
class DataStoreSavedPlaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : SavedPlaceRepository {
    private val adapter: JsonAdapter<List<StoredSavedPlaceRecord>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredSavedPlaceRecord::class.java)
    )

    override val savedPlaces: Flow<List<SavedPlaceRecord>> = context.savedPlaceDataStore.data.map { prefs ->
        SavedPlaceStorage.decode(prefs[SAVED_PLACES], adapter)
    }

    override fun observeSavedPlace(canonicalPlaceId: String): Flow<SavedPlaceRecord?> = savedPlaces.map { saved ->
        saved.firstOrNull { it.canonicalPlaceId == canonicalPlaceId }
    }

    override suspend fun upsert(place: SavedPlaceRecord) {
        context.savedPlaceDataStore.edit { prefs ->
            val existing = SavedPlaceStorage.decode(prefs[SAVED_PLACES], adapter)
            val updated = listOf(place) + existing.filterNot { it.canonicalPlaceId == place.canonicalPlaceId }
            prefs[SAVED_PLACES] = SavedPlaceStorage.encode(updated.sortedByDescending { it.savedAtEpochMillis }, adapter)
        }
    }

    override suspend fun remove(canonicalPlaceId: String) {
        context.savedPlaceDataStore.edit { prefs ->
            val updated = SavedPlaceStorage.decode(prefs[SAVED_PLACES], adapter)
                .filterNot { it.canonicalPlaceId == canonicalPlaceId }
            prefs[SAVED_PLACES] = SavedPlaceStorage.encode(updated, adapter)
        }
    }

    private companion object {
        val SAVED_PLACES = stringPreferencesKey("saved_places")
    }
}

internal object SavedPlaceStorage {
    fun encode(places: List<SavedPlaceRecord>, adapter: JsonAdapter<List<StoredSavedPlaceRecord>>): String =
        adapter.toJson(places.map(StoredSavedPlaceRecord::fromDomain))

    fun decode(rawValue: String?, adapter: JsonAdapter<List<StoredSavedPlaceRecord>>): List<SavedPlaceRecord> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(rawValue)?.map(StoredSavedPlaceRecord::toDomain).orEmpty()
        }.getOrDefault(emptyList())
    }
}

internal data class StoredSavedPlaceRecord(
    val canonicalPlaceId: String,
    val name: String,
    val typeLabel: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val sourceAttributions: List<ExploreSourceAttribution> = emptyList(),
    val savedAtEpochMillis: Long
) {
    fun toDomain() = SavedPlaceRecord(
        canonicalPlaceId = canonicalPlaceId,
        name = name,
        typeLabel = typeLabel,
        address = address,
        coordinate = Coordinate(latitude, longitude),
        sourceAttributions = sourceAttributions,
        savedAtEpochMillis = savedAtEpochMillis
    )

    companion object {
        fun fromDomain(place: SavedPlaceRecord) = StoredSavedPlaceRecord(
            canonicalPlaceId = place.canonicalPlaceId,
            name = place.name,
            typeLabel = place.typeLabel,
            address = place.address,
            latitude = place.coordinate.latitude,
            longitude = place.coordinate.longitude,
            sourceAttributions = place.sourceAttributions,
            savedAtEpochMillis = place.savedAtEpochMillis
        )
    }
}
