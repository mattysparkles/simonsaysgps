package com.simonsaysgps.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNavigationForegroundServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateTracker: NavigationServiceStateTracker
) : NavigationForegroundServiceController {

    override fun start(reason: String) {
        if (stateTracker.running.value) {
            Log.d(TAG, "Ignoring duplicate foreground service start. reason=$reason")
            return
        }

        Log.i(TAG, "Starting foreground navigation service. reason=$reason")
        ContextCompat.startForegroundService(
            context,
            Intent(context, NavigationForegroundService::class.java)
                .setAction(NavigationForegroundService.ACTION_START)
        )
    }

    override fun stop(reason: String) {
        if (!stateTracker.running.value) {
            Log.d(TAG, "Ignoring foreground service stop because it is not running. reason=$reason")
            return
        }

        Log.i(TAG, "Stopping foreground navigation service. reason=$reason")
        context.stopService(Intent(context, NavigationForegroundService::class.java))
    }

    companion object {
        private const val TAG = "NavFgServiceController"
    }
}
