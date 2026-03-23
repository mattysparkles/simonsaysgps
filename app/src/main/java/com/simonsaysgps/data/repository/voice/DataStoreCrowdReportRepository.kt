package com.simonsaysgps.data.repository.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.CrowdReportStatus
import com.simonsaysgps.domain.model.voice.CrowdReportType
import com.simonsaysgps.domain.repository.voice.CrowdReportRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.crowdReportDataStore by preferencesDataStore(name = "simonsays_voice_reports")

@Singleton
class DataStoreCrowdReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : CrowdReportRepository {
    private val reportsAdapter: JsonAdapter<List<StoredCrowdReport>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredCrowdReport::class.java)
    )
    private val pendingAdapter: JsonAdapter<StoredCrowdReport> = moshi.adapter(StoredCrowdReport::class.java)

    override val reports: Flow<List<CrowdReport>> = context.crowdReportDataStore.data.map { prefs ->
        CrowdReportStorage.decodeReports(prefs[SUBMITTED_REPORTS], reportsAdapter)
    }

    override val pendingReport: Flow<CrowdReport?> = context.crowdReportDataStore.data.map { prefs ->
        CrowdReportStorage.decodePending(prefs[PENDING_REPORT], pendingAdapter)
    }

    override suspend fun stage(report: CrowdReport) {
        context.crowdReportDataStore.edit { prefs ->
            prefs[PENDING_REPORT] = pendingAdapter.toJson(
                StoredCrowdReport.fromDomain(
                    report.copy(status = CrowdReportStatus.DRAFT, userConfirmed = false)
                )
            )
        }
    }

    override suspend fun confirmPending() {
        context.crowdReportDataStore.edit { prefs ->
            val current = CrowdReportStorage.decodePending(prefs[PENDING_REPORT], pendingAdapter) ?: return@edit
            val updated = CrowdReportStorage.decodeReports(prefs[SUBMITTED_REPORTS], reportsAdapter)
            val confirmed = current.copy(status = CrowdReportStatus.SUBMITTED, userConfirmed = true)
            prefs[SUBMITTED_REPORTS] = CrowdReportStorage.encodeReports(
                listOf(confirmed) + updated.filterNot { it.id == confirmed.id },
                reportsAdapter
            )
            prefs.remove(PENDING_REPORT)
        }
    }

    override suspend fun dismissPending(reason: String?) {
        context.crowdReportDataStore.edit { prefs ->
            prefs.remove(PENDING_REPORT)
        }
    }

    private companion object {
        val SUBMITTED_REPORTS = stringPreferencesKey("submitted_reports")
        val PENDING_REPORT = stringPreferencesKey("pending_report")
    }
}

internal object CrowdReportStorage {
    fun encodeReports(reports: List<CrowdReport>, adapter: JsonAdapter<List<StoredCrowdReport>>): String =
        adapter.toJson(reports.map(StoredCrowdReport::fromDomain))

    fun decodeReports(rawValue: String?, adapter: JsonAdapter<List<StoredCrowdReport>>): List<CrowdReport> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(rawValue)?.map(StoredCrowdReport::toDomain).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun decodePending(rawValue: String?, adapter: JsonAdapter<StoredCrowdReport>): CrowdReport? {
        if (rawValue.isNullOrBlank()) return null
        return runCatching {
            adapter.fromJson(rawValue)?.toDomain()
        }.getOrNull()
    }
}

internal data class StoredCrowdReport(
    val id: String,
    val timestampEpochMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val type: String,
    val transcriptNote: String?,
    val confidence: Float,
    val userConfirmed: Boolean,
    val status: String,
    val moderationNotes: String? = null
) {
    fun toDomain() = CrowdReport(
        id = id,
        timestampEpochMillis = timestampEpochMillis,
        location = if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null,
        type = CrowdReportType.valueOf(type),
        transcriptNote = transcriptNote,
        confidence = confidence,
        userConfirmed = userConfirmed,
        status = CrowdReportStatus.valueOf(status),
        moderationNotes = moderationNotes
    )

    companion object {
        fun fromDomain(report: CrowdReport) = StoredCrowdReport(
            id = report.id,
            timestampEpochMillis = report.timestampEpochMillis,
            latitude = report.location?.latitude,
            longitude = report.location?.longitude,
            type = report.type.name,
            transcriptNote = report.transcriptNote,
            confidence = report.confidence,
            userConfirmed = report.userConfirmed,
            status = report.status.name,
            moderationNotes = report.moderationNotes
        )
    }
}
