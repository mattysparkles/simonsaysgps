package com.simonsaysgps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simonsaysgps.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NavigationForegroundService : Service() {
    @Inject lateinit var stateTracker: NavigationServiceStateTracker

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action=$action startId=$startId flags=$flags")
        if (action != ACTION_START) {
            Log.w(TAG, "Unexpected action=$action. Keeping existing foreground session state.")
        }

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        stateTracker.markRunning()
        Log.i(TAG, "Foreground navigation service is active.")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Foreground navigation service is stopping.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stateTracker.markStopped()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.navigation_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.navigation_notification_channel_description)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.navigation_notification_title))
            .setContentText(getString(R.string.navigation_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.simonsaysgps.action.START_NAVIGATION_FOREGROUND"

        private const val TAG = "NavigationFgService"
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 1001
    }
}
