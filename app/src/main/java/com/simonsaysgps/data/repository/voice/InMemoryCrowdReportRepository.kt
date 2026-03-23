package com.simonsaysgps.data.repository.voice

import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.CrowdReportStatus
import com.simonsaysgps.domain.repository.voice.CrowdReportRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryCrowdReportRepository @Inject constructor() : CrowdReportRepository {
    private val reportStore = MutableStateFlow<List<CrowdReport>>(emptyList())
    private val stagedReport = MutableStateFlow<CrowdReport?>(null)

    override val reports: Flow<List<CrowdReport>> = reportStore.asStateFlow()
    override val pendingReport: Flow<CrowdReport?> = stagedReport.asStateFlow()

    override suspend fun stage(report: CrowdReport) {
        stagedReport.value = report.copy(status = CrowdReportStatus.DRAFT, userConfirmed = false)
    }

    override suspend fun confirmPending() {
        val current = stagedReport.value ?: return
        val confirmed = current.copy(status = CrowdReportStatus.SUBMITTED, userConfirmed = true)
        reportStore.update { listOf(confirmed) + it }
        stagedReport.value = null
    }

    override suspend fun dismissPending(reason: String?) {
        stagedReport.value = stagedReport.value?.copy(status = CrowdReportStatus.DISMISSED, moderationNotes = reason)
        stagedReport.value = null
    }
}
