package com.simonsaysgps.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.VisitHistoryEntry
import com.simonsaysgps.domain.model.VisitObservationSource
import com.simonsaysgps.domain.model.explore.ExploreProviderLink
import com.simonsaysgps.domain.repository.SettingsRepository
import com.simonsaysgps.domain.repository.VisitHistoryRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.visitHistoryDataStore by preferencesDataStore(name = "simonsays_visit_history")

@Singleton
class DataStoreVisitHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    moshi: Moshi
) : VisitHistoryRepository {
    private val adapter: JsonAdapter<List<StoredVisitHistoryEntry>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredVisitHistoryEntry::class.java)
    )

    override val visitHistory: Flow<List<VisitHistoryEntry>> = context.visitHistoryDataStore.data.combine(settingsRepository.settings) { prefs, settings ->
        VisitHistoryStorage.decode(prefs[VISIT_HISTORY], adapter)
            .prune(retentionDays = settings.exploreSettings.visitHistoryRetentionDays, nowEpochMillis = System.currentTimeMillis())
            .takeIf { settings.exploreSettings.visitHistoryEnabled }
            .orEmpty()
    }

    override suspend fun record(entry: VisitHistoryEntry) {
        val settings = settingsRepository.settings.first().exploreSettings
        if (!settings.visitHistoryEnabled) return
        context.visitHistoryDataStore.edit { prefs ->
            val existing = VisitHistoryStorage.decode(prefs[VISIT_HISTORY], adapter)
            val updated = (listOf(entry) + existing.filterNot { it.placeId == entry.placeId })
                .prune(retentionDays = settings.visitHistoryRetentionDays, nowEpochMillis = System.currentTimeMillis())
            prefs[VISIT_HISTORY] = VisitHistoryStorage.encode(updated, adapter)
        }
    }

    override suspend fun remove(placeId: String) {
        context.visitHistoryDataStore.edit { prefs ->
            val updated = VisitHistoryStorage.decode(prefs[VISIT_HISTORY], adapter).filterNot { it.placeId == placeId }
            prefs[VISIT_HISTORY] = VisitHistoryStorage.encode(updated, adapter)
        }
    }

    override suspend fun clear() {
        context.visitHistoryDataStore.edit { prefs ->
            prefs.remove(VISIT_HISTORY)
        }
    }

    private companion object {
        private val VISIT_HISTORY = stringPreferencesKey("visit_history")
    }
}

internal object VisitHistoryStorage {
    fun encode(
        visits: List<VisitHistoryEntry>,
        adapter: JsonAdapter<List<StoredVisitHistoryEntry>>
    ): String = adapter.toJson(visits.map(StoredVisitHistoryEntry::fromDomain))

    fun decode(
        rawValue: String?,
        adapter: JsonAdapter<List<StoredVisitHistoryEntry>>
    ): List<VisitHistoryEntry> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(rawValue)?.map(StoredVisitHistoryEntry::toDomain).orEmpty()
        }.getOrDefault(emptyList())
    }
}

internal fun List<VisitHistoryEntry>.prune(retentionDays: Int, nowEpochMillis: Long): List<VisitHistoryEntry> {
    if (retentionDays <= 0) return this.sortedByDescending { it.visitedAtEpochMillis }
    val cutoffMillis = nowEpochMillis - retentionDays * 24L * 60L * 60L * 1000L
    return filter { it.visitedAtEpochMillis >= cutoffMillis }.sortedByDescending { it.visitedAtEpochMillis }
}

internal data class StoredVisitHistoryEntry(
    val id: String,
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val visitedAtEpochMillis: Long,
    val confidence: Float,
    val source: String,
    val providerLinks: List<ExploreProviderLink> = emptyList(),
    val notes: String? = null
) {
    fun toDomain() = VisitHistoryEntry(
        id = id,
        placeId = placeId,
        name = name,
        address = address,
        coordinate = Coordinate(latitude, longitude),
        visitedAtEpochMillis = visitedAtEpochMillis,
        confidence = confidence,
        source = VisitObservationSource.valueOf(source),
        providerLinks = providerLinks,
        notes = notes
    )

    companion object {
        fun fromDomain(entry: VisitHistoryEntry) = StoredVisitHistoryEntry(
            id = entry.id,
            placeId = entry.placeId,
            name = entry.name,
            address = entry.address,
            latitude = entry.coordinate.latitude,
            longitude = entry.coordinate.longitude,
            visitedAtEpochMillis = entry.visitedAtEpochMillis,
            confidence = entry.confidence,
            source = entry.source.name,
            providerLinks = entry.providerLinks,
            notes = entry.notes
        )
    }
}
