package com.igancao.hptasr.foreground_service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.igancao.hptasr.HptAsr
import com.igancao.hptasr.R

internal class AsrForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ForegroundServiceManager.ACTION_STOP -> {
                ForegroundServiceManager.markStopped()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> startInForeground()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ForegroundServiceManager.markStopped()
        HptAsr.handleHostTaskRemoved()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ForegroundServiceManager.markStopped()
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = ensureNotificationChannel()
        val smallIcon = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_btn_speak_now
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.hptasr_notification_title))
            .setContentText(getString(R.string.hptasr_notification_text))
            .setSmallIcon(smallIcon)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                ForegroundServiceManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(ForegroundServiceManager.NOTIFICATION_ID, notification)
        }
        ForegroundServiceManager.markRunning()
    }

    private fun ensureNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(ForegroundServiceManager.CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    ForegroundServiceManager.CHANNEL_ID,
                    getString(R.string.hptasr_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.hptasr_notification_channel_description)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
        return ForegroundServiceManager.CHANNEL_ID
    }
}
