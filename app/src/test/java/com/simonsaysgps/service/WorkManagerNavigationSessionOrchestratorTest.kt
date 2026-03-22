package com.simonsaysgps.service

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.repository.NavigationSessionRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WorkManagerNavigationSessionOrchestratorTest {
    private val repository = mockk<NavigationSessionRepository>()
    private val workManager = mockk<WorkManager>()
    private val foregroundServiceController = mockk<NavigationForegroundServiceController>(relaxed = true)
    private val operation = mockk<Operation>(relaxed = true)

    @Test
    fun `active session is persisted and recovery work is scheduled`() = runTest {
        coEvery { repository.save(any()) } returns Unit
        every { workManager.enqueueUniqueWork(any(), any(), any()) } returns operation
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns operation

        val orchestrator = WorkManagerNavigationSessionOrchestrator(repository, workManager, foregroundServiceController)
        val state = NavigationSessionState(
            route = Route(
                geometry = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.1)),
                maneuvers = emptyList(),
                totalDistanceMeters = 10.0,
                totalDurationSeconds = 5.0,
                etaEpochSeconds = 10L
            ),
            navigationActive = true
        )

        orchestrator.syncSession(state)

        coVerify { repository.save(state) }
        verify { workManager.enqueueUniqueWork("navigation-session-restore-immediate", ExistingWorkPolicy.REPLACE, any()) }
        verify { workManager.enqueueUniquePeriodicWork("navigation-session-restore-periodic", ExistingPeriodicWorkPolicy.UPDATE, any()) }
    }

    @Test
    fun `inactive session clears persistence and cancels recovery work`() = runTest {
        coEvery { repository.clear() } returns Unit
        every { workManager.cancelUniqueWork(any()) } returns operation

        val orchestrator = WorkManagerNavigationSessionOrchestrator(repository, workManager, foregroundServiceController)

        orchestrator.syncSession(NavigationSessionState())

        coVerify { repository.clear() }
        verify { workManager.cancelUniqueWork("navigation-session-restore-immediate") }
        verify { workManager.cancelUniqueWork("navigation-session-restore-periodic") }
    }

    @Test
    fun `restoreSession returns persisted state and schedules recovery when active`() = runTest {
        val restored = NavigationSessionState(navigationActive = true)
        coEvery { repository.read() } returns restored
        every { workManager.enqueueUniqueWork(any(), any(), any()) } returns operation
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns operation

        val orchestrator = WorkManagerNavigationSessionOrchestrator(repository, workManager, foregroundServiceController)

        val result = orchestrator.restoreSession()

        assertThat(result).isEqualTo(restored)
        verify { workManager.enqueueUniqueWork("navigation-session-restore-immediate", ExistingWorkPolicy.REPLACE, any()) }
        verify { workManager.enqueueUniquePeriodicWork("navigation-session-restore-periodic", ExistingPeriodicWorkPolicy.UPDATE, any()) }
    }
}
