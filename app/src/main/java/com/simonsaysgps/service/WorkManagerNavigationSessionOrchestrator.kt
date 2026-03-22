package com.simonsaysgps.service

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.repository.NavigationSessionRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import com.simonsaysgps.domain.service.NavigationSessionOrchestrator
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerNavigationSessionOrchestrator @Inject constructor(
    private val navigationSessionRepository: NavigationSessionRepository,
    private val workManager: WorkManager,
    private val navigationForegroundServiceController: NavigationForegroundServiceController
) : NavigationSessionOrchestrator {

    override suspend fun restoreSession(): NavigationSessionState? {
        val restoredState = navigationSessionRepository.read()
        if (restoredState?.navigationActive == true) {
            scheduleRecoveryWork()
        }
        return restoredState
    }

    override suspend fun syncSession(state: NavigationSessionState) {
        if (state.navigationActive) {
            navigationSessionRepository.save(state)
            scheduleRecoveryWork()
        } else {
            navigationSessionRepository.clear()
            cancelRecoveryWork()
        }
    }

    override fun ensureForegroundService(reason: String) {
        navigationForegroundServiceController.start(reason)
    }

    private fun scheduleRecoveryWork() {
        workManager.enqueueUniqueWork(
            IMMEDIATE_RESTORE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NavigationSessionRestoreWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_RESTORE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NavigationSessionRestoreWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    private fun cancelRecoveryWork() {
        workManager.cancelUniqueWork(IMMEDIATE_RESTORE_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_RESTORE_WORK_NAME)
    }

    companion object {
        private const val IMMEDIATE_RESTORE_WORK_NAME = "navigation-session-restore-immediate"
        private const val PERIODIC_RESTORE_WORK_NAME = "navigation-session-restore-periodic"
    }
}
