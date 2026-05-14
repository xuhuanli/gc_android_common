package com.igancao.hptasr.foreground_service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.igancao.hptasr.HPT_ASR_TAG

internal object ForegroundServiceManager {
    internal const val ACTION_START = "com.igancao.hptasr.action.START_RECORDING"
    internal const val ACTION_STOP = "com.igancao.hptasr.action.STOP_RECORDING"
    internal const val CHANNEL_ID = "fusionasr_recording"
    internal const val NOTIFICATION_ID = 2001

    @Volatile
    private var runningInForeground = false

    fun isRunning(): Boolean = runningInForeground

    fun start(context: Context): Boolean {
        if (isRunning()) {
            Log.d(HPT_ASR_TAG, "Foreground service already running, skip start request")
            return true
        }
        return runCatching {
            val intent = Intent(context, AsrForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }.onSuccess {
            Log.d(HPT_ASR_TAG, "Foreground service start requested")
        }.onFailure {
            Log.w(HPT_ASR_TAG, "Failed to start foreground service", it)
        }.isSuccess
    }

    fun stop(context: Context) {
        if (!isRunning()) return
        runCatching {
            context.stopService(Intent(context, AsrForegroundService::class.java))
        }.onSuccess {
            Log.d(HPT_ASR_TAG, "Foreground service stop requested")
        }.onFailure {
            Log.w(HPT_ASR_TAG, "Failed to stop foreground service", it)
        }
    }

    internal fun markRunning() {
        runningInForeground = true
    }

    internal fun markStopped() {
        runningInForeground = false
    }
}
