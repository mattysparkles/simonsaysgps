package com.simonsaysgps.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simonsaysgps.domain.repository.NavigationSessionRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NavigationSessionRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val navigationSessionRepository: NavigationSessionRepository,
    private val navigationForegroundServiceController: NavigationForegroundServiceController
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionState = navigationSessionRepository.read()
        if (sessionState?.navigationActive == true) {
            navigationForegroundServiceController.start("restoring persisted navigation session")
        }
        return Result.success()
    }
}
