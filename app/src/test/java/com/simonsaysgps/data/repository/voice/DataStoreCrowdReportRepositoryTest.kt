package com.simonsaysgps.data.repository.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.CrowdReportStatus
import com.simonsaysgps.domain.model.voice.CrowdReportType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreCrowdReportRepositoryTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `confirmed reports persist across repository instances`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reportId = "persisted-report-${System.currentTimeMillis()}"
        val repository = DataStoreCrowdReportRepository(context, moshi)

        repository.stage(
            CrowdReport(
                id = reportId,
                timestampEpochMillis = 1234L,
                location = Coordinate(37.0, -122.0),
                type = CrowdReportType.TRAFFIC,
                transcriptNote = "heavy slowdown",
                confidence = 0.87f,
                userConfirmed = false,
                status = CrowdReportStatus.DRAFT
            )
        )
        repository.confirmPending()

        val reloaded = DataStoreCrowdReportRepository(context, moshi)
        val restored = reloaded.reports.first().first { it.id == reportId }

        assertThat(restored.status).isEqualTo(CrowdReportStatus.SUBMITTED)
        assertThat(restored.userConfirmed).isTrue()
        assertThat(restored.transcriptNote).isEqualTo("heavy slowdown")
    }
}
