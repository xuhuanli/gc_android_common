package com.igancao.hptasr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.igancao.hptasr.bean.AudioInfo
import com.igancao.hptasr.foreground_service.ForegroundServiceManager
import com.igancao.hptasr.HptAsrSystemConfig
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

internal const val HPT_ASR_TAG = "HptAsr"

object HptAsr {

    private val stateLock = Any()
    private val generation = AtomicLong(0)

    @Volatile private var cachedAudioInfo: AudioInfo? = null
    @Volatile private var asrEngine: AsrEngine? = null
    @Volatile private var cachedTaskId: String? = null
    @Volatile private var cachedSocketURL: String? = null
    @Volatile private var cachedAppContext: Context? = null
    @Volatile private var currentManagedListener: AsrListener? = null
    @Volatile private var cachedAutoManageForegroundService = false

    private data class ActiveSession(
        val engine: AsrEngine,
        val taskId: String,
        val socketURL: String,
        val appContext: Context,
        val listener: AsrListener,
        val autoManageForegroundService: Boolean,
    )

    fun setUpAudioConfigInfo(configInfo: JSONObject) {
        val audioInfo = AudioInfo.fromConfigJson(configInfo)
        synchronized(stateLock) {
            check(asrEngine == null) { "stop() the current session before calling setUpAudioConfigInfo()" }
        }
        Log.d(HPT_ASR_TAG, "v${BuildConfig.LIBRARY_VERSION} setUpAudioConfigInfo: $configInfo")
        cachedAudioInfo = audioInfo
    }

    fun setUpAudioRecorderWithTaskId(
        context: Context,
        taskId: String,
        socketURL: String,
        listener: AsrListener,
    ) {
        setUpAudioRecorderWithTaskId(
            context = context,
            taskId = taskId,
            socketURL = socketURL,
            autoManageForegroundService = false,
            listener = listener,
        )
    }

    fun setUpAudioRecorderWithTaskId(
        context: Context,
        taskId: String,
        socketURL: String,
        autoManageForegroundService: Boolean,
        listener: AsrListener,
    ) {
        val audioInfo = synchronized(stateLock) {
            checkNotNull(cachedAudioInfo) { "call setUpAudioConfigInfo() before setUpAudioRecorderWithTaskId()" }
            check(asrEngine == null) { "stop() the current session before calling setUpAudioRecorderWithTaskId() again" }
            cachedAudioInfo!!
        }

        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(socketURL.isNotBlank()) { "socketURL must not be blank" }

        val identifier = HptAsrSystemConfig.identifier
        val ticket = HptAsrSystemConfig.ticket
        val typeItemCode = HptAsrSystemConfig.typeItemCode
        require(identifier.isNotBlank() || (ticket.isNotBlank() && typeItemCode.isNotBlank())) {
            "identifier or (ticket + typeItemCode) must not be blank"
        }

        val gen = generation.get()
        val managed = createManagedListener(listener, gen)

        val engine = AsrEngine(
            appContext = context.applicationContext,
            audioInfo = audioInfo,
            asrListener = managed,
        )

        synchronized(stateLock) {
            check(asrEngine == null) { "stop() the current session before calling setUpAudioRecorderWithTaskId() again" }
            asrEngine = engine
            cachedTaskId = taskId
            cachedSocketURL = socketURL
            cachedAppContext = context.applicationContext
            currentManagedListener = managed
            cachedAutoManageForegroundService = autoManageForegroundService
        }
    }

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    fun start() {
        val session = synchronized(stateLock) {
            val e = asrEngine ?: return@synchronized null
            val t = cachedTaskId ?: return@synchronized null
            val u = cachedSocketURL ?: return@synchronized null
            val a = cachedAppContext ?: return@synchronized null
            val l = currentManagedListener ?: return@synchronized null
            ActiveSession(e, t, u, a, l, cachedAutoManageForegroundService)
        } ?: throw IllegalStateException("call setUpAudioRecorderWithTaskId() first")

        val permissionDenied = ContextCompat.checkSelfPermission(
            session.appContext,
            Manifest.permission.RECORD_AUDIO,
        ) != PackageManager.PERMISSION_GRANTED
        if (permissionDenied) {
            stopManagedForegroundService(session)
            session.listener.onSessionError("RECORD_AUDIO permission not granted")
            session.listener.onStateChanged(AsrSessionState.ENDED, AsrStateReason.ERROR)
            synchronized(stateLock) {
                if (asrEngine === session.engine) {
                    clearSessionLocked()
                }
            }
            return
        }

        when {
            session.engine.isActiveOrInFlight() -> {
                Log.w(HPT_ASR_TAG, "start() ignored: session already active or in-flight")
            }
            session.engine.isPausedOrPausing() -> {
                if (!startManagedForegroundService(session, endSessionOnFailure = false)) return
                session.engine.resume()
            }
            else -> {
                if (!startManagedForegroundService(session, endSessionOnFailure = true)) return
                val started = session.engine.start(taskId = session.taskId, url = session.socketURL)
                if (!started) {
                    stopManagedForegroundService(session)
                    synchronized(stateLock) {
                        if (asrEngine === session.engine) {
                            clearSessionLocked()
                        }
                    }
                }
            }
        }
    }

    fun pause() {
        val appContext = synchronized(stateLock) {
            asrEngine ?: return
            cachedAppContext.takeIf { cachedAutoManageForegroundService }
        }
        asrEngine?.pause()
        appContext?.let { ForegroundServiceManager.stop(it) }
    }

    /**
     * 结束当前会话并释放资源。
     *
     * 该方法是同步调用，停止录音时可能短暂等待采集线程退出，建议在非主线程调用。
     */
    fun stop() {
        stopInternal(AsrStateReason.NORMAL_STOP)
    }

    internal fun handleHostTaskRemoved() {
        stopInternal(AsrStateReason.APP_TASK_REMOVED)
    }

    private fun stopInternal(reason: AsrStateReason) {
        val appContextToStop = synchronized(stateLock) {
            cachedAppContext.takeIf { cachedAutoManageForegroundService }
        }
        val engineToStop = synchronized(stateLock) { asrEngine }
        engineToStop?.stop(reason)
        appContextToStop?.let { ForegroundServiceManager.stop(it) }
        synchronized(stateLock) {
            if (asrEngine === engineToStop) {
                clearSessionLocked()
            }
        }
    }

    private fun startManagedForegroundService(
        session: ActiveSession,
        endSessionOnFailure: Boolean,
    ): Boolean {
        if (!session.autoManageForegroundService) return true
        if (ForegroundServiceManager.start(session.appContext)) return true

        session.listener.onSessionError("Foreground service start failed")
        if (endSessionOnFailure) {
            session.listener.onStateChanged(AsrSessionState.ENDED, AsrStateReason.ERROR)
            synchronized(stateLock) {
                if (asrEngine === session.engine) {
                    clearSessionLocked()
                }
            }
        }
        return false
    }

    private fun stopManagedForegroundService(session: ActiveSession) {
        if (session.autoManageForegroundService) {
            ForegroundServiceManager.stop(session.appContext)
        }
    }

    private fun clearSessionLocked() {
        generation.incrementAndGet()
        asrEngine = null
        cachedTaskId = null
        cachedSocketURL = null
        cachedAppContext = null
        currentManagedListener = null
        cachedAutoManageForegroundService = false
    }

    private fun createManagedListener(delegate: AsrListener, gen: Long): AsrListener {
        return object : AsrListener {
            override fun onReady() {
                if (isCurrentGen(gen)) delegate.onReady()
            }

            override fun onWsMessage(text: String) {
                if (isCurrentGen(gen)) delegate.onWsMessage(text)
            }

            override fun onSessionError(error: String) {
                if (isCurrentGen(gen)) delegate.onSessionError(error)
            }

            override fun onDecibelsChanged(db: Double) {
                if (isCurrentGen(gen)) delegate.onDecibelsChanged(db)
            }

            override fun onRecordingDurationChanged(durationSeconds: Double) {
                if (isCurrentGen(gen)) delegate.onRecordingDurationChanged(durationSeconds)
            }

            override fun onWsOpen() {
                if (isCurrentGen(gen)) delegate.onWsOpen()
            }

            override fun onWsClosed(code: Int, reason: String) {
                if (isCurrentGen(gen)) delegate.onWsClosed(code, reason)
            }

            override fun onWsFailure(error: String, code: Int?) {
                if (isCurrentGen(gen)) delegate.onWsFailure(error, code)
            }

            override fun onStateChanged(state: AsrSessionState, reason: AsrStateReason) {
                var appContextToStop: Context? = null
                val deliver = synchronized(stateLock) {
                    val ok = isCurrentGenLocked(gen)
                    if (ok && cachedAutoManageForegroundService) {
                        when {
                            state == AsrSessionState.ENDED -> {
                                appContextToStop = cachedAppContext
                                clearSessionLocked()
                            }
                            state == AsrSessionState.PAUSED &&
                                reason != AsrStateReason.AUDIO_FOCUS_LOSS_TRANSIENT -> {
                                appContextToStop = cachedAppContext
                            }
                        }
                    } else if (state == AsrSessionState.ENDED && ok) {
                        clearSessionLocked()
                    }
                    ok
                }
                appContextToStop?.let { ForegroundServiceManager.stop(it) }
                if (deliver) delegate.onStateChanged(state, reason)
            }
        }
    }

    private fun isCurrentGen(gen: Long): Boolean =
        synchronized(stateLock) { isCurrentGenLocked(gen) }

    private fun isCurrentGenLocked(gen: Long): Boolean =
        generation.get() == gen && asrEngine != null
}
