package com.igancao.hptasr

import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.igancao.hptasr.bean.AudioInfo
import com.igancao.hptasr.transport.WebSocketManager
import com.igancao.hptasr.transport.WsListener
import com.igancao.hptasr.transport.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

private const val START_TIMEOUT_MS = 30_000L

/**
 * Android 侧 ASR 会话引擎。
 *
 * 负责串起以下能力：
 * 1. 录音采集与音频焦点管理（委托给 AsrAudioRecorder）
 * 2. WebSocket 建连、断连与音频推流
 * 3. pause/resume/stop 的会话状态流转
 * 4. WebSocket 文本消息透传与状态回调
 */
internal class AsrEngine(
    private val appContext: Context?,
    private val audioInfo: AudioInfo,
    private val asrListener: AsrListener,
) : WsListener {

    /** 引擎运行阶段。ACTIVE 表示当前会话可正常推流与接收结果。 */
    private enum class EnginePhase {
        IDLE,
        CONNECTING,
        ACTIVE,
        PAUSING,
        PAUSED,
        RESUMING,
    }

    private data class DeferredResumeRequest(
        val reason: AsrStateReason,
        val shouldRequestAudioFocus: Boolean,
    )

    /** 引擎运行时状态，统一描述会话、连接和恢复过程。 */
    private data class EngineState(
        val phase: EnginePhase = EnginePhase.IDLE,
        val pauseReason: AsrStateReason? = null,
        val resumeReason: AsrStateReason? = null,
        val hasConnectedOnce: Boolean = false,
    )

    /** WebSocket 传输层，负责建连、发包和断线重连。 */
    private val webSocketManager = WebSocketManager()
    /** 引擎内部状态，使用原子引用保证多线程下读取一致。 */
    private val engineState = AtomicReference(EngineState())
    /** ACTIVE -> PAUSING 期间的延迟断链任务，避免旧任务在恢复后误伤新连接。 */
    @Volatile
    private var pendingPauseDisconnectJob: Job? = null
    /** stop() 发送 END 后等待服务端确认的延迟 shutdown 任务。 */
    @Volatile
    private var pendingStopShutdownJob: Job? = null
    /** start() 到首次 WebSocket open/Ready 之前的首连超时任务。 */
    @Volatile
    private var startupTimeoutJob: Job? = null
    /** 恢复后等待首次有效音频帧时再发出的 RESUMED 原因。 */
    private val pendingResumedReason = AtomicReference<AsrStateReason?>(null)
    /** PAUSING 期间先缓存 resume 请求，等真正进入 PAUSED 后再继续恢复。 */
    private val deferredResumeRequest = AtomicReference<DeferredResumeRequest?>(null)

    /** 音频采集器，负责录音、音频焦点与前台服务。 */
    private val audioRecorder = AsrAudioRecorder(
        context = appContext,
        audioInfo = audioInfo,
        callback = object : AsrAudioRecorder.Callback {
            override fun isRecordingActive() = engineState.get().phase == EnginePhase.ACTIVE
            override fun isSessionEnded() = engineState.get().phase == EnginePhase.IDLE
            override fun onAudioData(data: ByteArray) {
                pendingResumedReason.getAndSet(null)?.let { reason ->
                    asrListener.onStateChanged(AsrSessionState.RESUMED, reason)
                    Log.d(HPT_ASR_TAG, "Recording resumed after first audio frame, reason=$reason")
                }
                webSocketManager.send(WsMessage.Binary(data))
            }
            override fun onDecibelsChanged(db: Double) {
                asrListener.onDecibelsChanged(db)
            }
            override fun onRecordingDurationChanged(durationSeconds: Double) {
                asrListener.onRecordingDurationChanged(durationSeconds)
            }
            override fun onAudioFocusLoss() = handleAudioFocusLoss()
            override fun onAudioFocusLossTransient() = handleTransientAudioFocusLoss()
            override fun onAudioFocusGain() = handleAudioFocusGain()
        },
    )

    /**
     * 启动 ASR 引擎
     * @param url WebSocket 连接地址
     */
    // 返回值用于让入口层判断这次 start 是否真正建立了会话，避免失败后残留脏状态。
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    fun start(taskId: String, url: String): Boolean {
        if (beginSession()) {
            pendingResumedReason.set(null)

            if (!audioRecorder.requestAudioFocus()) {
                resetEngineState()
                asrListener.onSessionError("Audio focus request denied")
                asrListener.onStateChanged(AsrSessionState.ENDED, AsrStateReason.ERROR)
                return false
            }

            try {
                audioRecorder.initAudioRecord()
                scheduleStartupTimeout()
                webSocketManager.init(url, this, taskId)
                audioRecorder.startLoop()
                return true
            } catch (e: Exception) {
                cancelStartupTimeout()
                resetEngineState()
                webSocketManager.shutdown()
                val loopStopped = runCatching {
                    audioRecorder.stopLoopAndRecording()
                }.onFailure {
                    Log.w(HPT_ASR_TAG, "Failed to stop recorder after initialization failure", it)
                }.getOrDefault(false)
                audioRecorder.releaseIfLoopStopped(loopStopped)
                audioRecorder.abandonAudioFocus()
                asrListener.onSessionError(e.message ?: "Initialization failed")
                asrListener.onStateChanged(AsrSessionState.ENDED, AsrStateReason.ERROR)
                Log.e(HPT_ASR_TAG, "Initialization failed", e)
                return false
            }
        } else {
            Log.w(HPT_ASR_TAG, "Already started")
            return false
        }
    }

    /**
     * 暂停
     * 没有在录制音频直接返回
     */
    fun pause() {
        pauseInternal(reason = AsrStateReason.MANUAL)
    }

    /** 内部暂停实现，支持手动暂停与音频焦点中断共用同一套流程。 */
    private fun pauseInternal(
        reason: AsrStateReason,
        abandonFocus: Boolean = true,
    ) {
        val previousState = pauseSession(reason) ?: return
        pendingResumedReason.set(null)
        deferredResumeRequest.set(null)
        cancelPendingPauseDisconnect()

        try {
            audioRecorder.stopRecordingIfNeeded()
            if (previousState.phase == EnginePhase.ACTIVE) {
                webSocketManager.send(WsMessage.Text("PAUSE"))
                pendingPauseDisconnectJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        delay(150)
                        webSocketManager.disconnect()
                        finishPauseIfNeeded(reason)
                    } finally {
                        pendingPauseDisconnectJob = null
                    }
                }
            } else {
                webSocketManager.disconnect()
                finishPauseIfNeeded(reason)
            }
            if (abandonFocus) {
                audioRecorder.abandonAudioFocus()
            }
        } catch (e: Exception) {
            asrListener.onSessionError(e.message ?: "Pause failed")
            Log.e(HPT_ASR_TAG, "Failed to pause recording", e)
        }
    }

    /** 当前会话是否处于可以调用 resume() 的暂停/暂停中态。供 HptAsr.start() 分发恢复路径使用。 */
    internal fun isPausedOrPausing(): Boolean {
        val p = engineState.get().phase
        return p == EnginePhase.PAUSED || p == EnginePhase.PAUSING
    }

    /** 当前会话是否处于活跃/连接/恢复中态，此时再调用 start() 应被拒绝。 */
    internal fun isActiveOrInFlight(): Boolean {
        val p = engineState.get().phase
        return p == EnginePhase.ACTIVE || p == EnginePhase.CONNECTING || p == EnginePhase.RESUMING
    }

    /**
     * 恢复录音。
     *
     * 对齐 iOS 语义：恢复时不复用旧连接，而是保留原 taskId 重新建立 WebSocket，
     * 待连接成功后再真正恢复音频采集。
     *
     * internal：由 HptAsr.start() 在 PAUSED/PAUSING 态下调用，以及内部 handleAudioFocusGain 自动恢复使用。
     */
    internal fun resume() {
        resumeInternal(
            reason = AsrStateReason.MANUAL,
            shouldRequestAudioFocus = true,
        )
    }

    /**
     * 恢复录音
     * shouldRequestAudioFocus 本次恢复是否需要主动重新申请音频焦点
     */
    private fun resumeInternal(
        reason: AsrStateReason,
        shouldRequestAudioFocus: Boolean,
    ) {
        val currentState = engineState.get()
        if (currentState.phase == EnginePhase.PAUSING) {
            deferredResumeRequest.set(
                DeferredResumeRequest(
                    reason = reason,
                    shouldRequestAudioFocus = shouldRequestAudioFocus,
                ),
            )
            Log.d(HPT_ASR_TAG, "Resume requested while pause is still completing, defer until PAUSED, reason=$reason")
            return
        }
        if (currentState.phase != EnginePhase.PAUSED) return

        if (shouldRequestAudioFocus && !audioRecorder.requestAudioFocus()) {
            asrListener.onSessionError("Audio focus request denied")
            return
        }

        try {
            // 进入 RESUMING 后等待 WebSocket 建连成功，再恢复录音。
            updateEngineState {
                it.copy(
                    phase = EnginePhase.RESUMING,
                    pauseReason = null,
                    resumeReason = reason,
                )
            }
            pendingResumedReason.set(reason)
            webSocketManager.reconnectNow()
            Log.d(HPT_ASR_TAG, "Resume requested, reason=$reason, waiting for websocket reconnect")
        } catch (e: Exception) {
            updateEngineState {
                it.copy(
                    phase = EnginePhase.PAUSED,
                    pauseReason = AsrStateReason.ERROR,
                    resumeReason = null,
                )
            }
            pendingResumedReason.set(null)
            if (shouldRequestAudioFocus) {
                audioRecorder.abandonAudioFocus()
            }
            asrListener.onSessionError(e.message ?: "Resume failed")
            Log.e(HPT_ASR_TAG, "Failed to resume recording", e)
        }
    }

    /**
     * 结束当前会话。
     *
     * stop 会彻底关闭录音、释放资源并结束会话；与 pause 不同，stop 之后不能再 resume。
     */
    fun stop(reason: AsrStateReason = AsrStateReason.NORMAL_STOP) {
        val previousState = endSession() ?: return
        cancelStartupTimeout()
        val shouldSendEnd = previousState.phase == EnginePhase.ACTIVE &&
            (reason == AsrStateReason.NORMAL_STOP || reason == AsrStateReason.APP_TASK_REMOVED)
        cancelPendingPauseDisconnect()
        pendingStopShutdownJob?.cancel()
        pendingStopShutdownJob = null
        deferredResumeRequest.set(null)

        try {
            val loopStopped = audioRecorder.stopLoopAndRecording()
            audioRecorder.releaseIfLoopStopped(loopStopped)
            if (shouldSendEnd) {
                webSocketManager.send(WsMessage.Text("END"))
                pendingStopShutdownJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(150)
                    webSocketManager.shutdown()
                    pendingStopShutdownJob = null
                }
            } else {
                webSocketManager.shutdown()
            }
        } catch (e: Exception) {
            asrListener.onSessionError(e.message ?: "Stop failed")
            Log.e(HPT_ASR_TAG, "Stop failed", e)
        } finally {
            audioRecorder.abandonAudioFocus()
            pendingResumedReason.set(null)
            asrListener.onStateChanged(
                AsrSessionState.ENDED,
                reason,
            )
        }
    }

    /** WebSocket 建连成功后的回调。 */
    override fun onOpen() {
        Log.i(HPT_ASR_TAG, "WebSocket opened")
        // previousState 为更新前状态，currentState 为更新后状态。
        val (previousState, currentState) = updateEngineStateAndGetBeforeAfter { state ->
            when (state.phase) {
                EnginePhase.CONNECTING,
                EnginePhase.RESUMING,
                -> state.copy(
                    phase = EnginePhase.ACTIVE,
                    hasConnectedOnce = true,
                )

                else -> state
            }
        }
        val didEnterActive = previousState.phase != currentState.phase &&
            currentState.phase == EnginePhase.ACTIVE
        if (didEnterActive) {
            cancelStartupTimeout()
            audioRecorder.startRecordingIfNeeded()
        }
        asrListener.onWsOpen()
        when {
            previousState.phase == EnginePhase.RESUMING && didEnterActive -> {
                val resumeReason = previousState.resumeReason ?: AsrStateReason.MANUAL
                Log.d(
                    HPT_ASR_TAG,
                    "WebSocket reconnected, waiting for first audio frame before reporting resumed, reason=$resumeReason"
                )
            }

            previousState.phase == EnginePhase.CONNECTING &&
                !previousState.hasConnectedOnce &&
                didEnterActive
            -> {
                asrListener.onStateChanged(AsrSessionState.READY, AsrStateReason.MANUAL)
                asrListener.onReady()
            }
        }
    }

    /** WebSocket 彻底关闭后的回调。 */
    override fun onClosed(code: Int, reason: String) {
        asrListener.onWsClosed(code, reason)
        updateEngineState {
            when (it.phase) {
                EnginePhase.ACTIVE -> it.copy(phase = EnginePhase.CONNECTING)
                else -> it
            }
        }
    }

    /** 透传后端文本消息给上层。 */
    override fun onTextMessage(text: String) {
        Log.d(HPT_ASR_TAG, "Received: $text")
        // 仅在 ACTIVE 阶段透传消息，避免停止、暂停或过渡期的迟到消息继续触达业务层。
        if (engineState.get().phase != EnginePhase.ACTIVE) {
            Log.d(HPT_ASR_TAG, "Drop text message in non-ACTIVE phase: ${engineState.get().phase}")
            return
        }
        asrListener.onWsMessage(text)
    }

    /** 连接异常时的回调。pause 态下的断链属于预期，不额外上报。 */
    override fun onFailure(t: Throwable, code: Int?) {
        Log.e(HPT_ASR_TAG, "WebSocket failure, code=$code", t)
        asrListener.onWsFailure(t.message ?: "WebSocket failure", code)
        val currentState = engineState.get()
        if (currentState.phase == EnginePhase.IDLE || currentState.phase == EnginePhase.PAUSED) {
            return
        }

        updateEngineState {
            when (it.phase) {
                EnginePhase.ACTIVE -> it.copy(phase = EnginePhase.CONNECTING)
                else -> it
            }
        }
    }

    /** 音频焦点丢失时，将当前会话切到暂停态，具体原因由 AUDIO_FOCUS_LOSS 表达。 */
    private fun handleAudioFocusLoss() {
        if (engineState.get().phase == EnginePhase.IDLE) return
        pauseInternal(reason = AsrStateReason.AUDIO_FOCUS_LOSS)
    }

    /**
     * 临时音频焦点丢失时，暂停当前会话但保留焦点监听，
     * 这样系统归还焦点时仍能收到 AUDIOFOCUS_GAIN。
     */
    private fun handleTransientAudioFocusLoss() {
        if (engineState.get().phase == EnginePhase.IDLE) return
        pauseInternal(
            reason = AsrStateReason.AUDIO_FOCUS_LOSS_TRANSIENT,
            abandonFocus = false,
        )
    }

    /** 临时焦点恢复后自动继续会话。 */
    private fun handleAudioFocusGain() {
        val currentState = engineState.get()
        if (
            (currentState.phase == EnginePhase.PAUSING || currentState.phase == EnginePhase.PAUSED) &&
            currentState.pauseReason == AsrStateReason.AUDIO_FOCUS_LOSS_TRANSIENT
        ) {
            Log.d(HPT_ASR_TAG, "Audio focus regained, resuming session automatically")
            resumeInternal(
                reason = AsrStateReason.AUDIO_FOCUS_GAIN,
                shouldRequestAudioFocus = false,
            )
            return
        }
        Log.d(HPT_ASR_TAG, "Audio focus regained, currentState=$currentState")
    }

    /** 从 IDLE 切换到 CONNECTING，表示一次新会话开始并等待首连成功。 */
    private fun beginSession(): Boolean {
        while (true) {
            val current = engineState.get()
            if (current.phase != EnginePhase.IDLE) {
                return false
            }
            val updated = EngineState(phase = EnginePhase.CONNECTING)
            if (engineState.compareAndSet(current, updated)) {
                return true
            }
        }
    }

    /** 结束当前会话并重置为 IDLE，返回结束前的状态快照。 */
    private fun endSession(): EngineState? {
        while (true) {
            val current = engineState.get()
            if (current.phase == EnginePhase.IDLE) {
                return null
            }
            if (engineState.compareAndSet(current, EngineState())) {
                return current
            }
        }
    }

    /** 将当前会话切到 PAUSED，并记录暂停原因。 */
    private fun pauseSession(reason: AsrStateReason): EngineState? {
        while (true) {
            val current = engineState.get()
            if (current.phase == EnginePhase.IDLE) {
                return null
            }
            if (
                (current.phase == EnginePhase.PAUSED || current.phase == EnginePhase.PAUSING) &&
                current.pauseReason == reason
            ) {
                return null
            }
            val updated = current.copy(
                phase = if (current.phase == EnginePhase.ACTIVE) EnginePhase.PAUSING else EnginePhase.PAUSED,
                pauseReason = reason,
                resumeReason = null,
            )
            if (engineState.compareAndSet(current, updated)) {
                return current
            }
        }
    }

    /** 把引擎状态整体重置为空闲态。 */
    private fun resetEngineState() {
        engineState.set(EngineState())
    }

    private fun scheduleStartupTimeout() {
        cancelStartupTimeout()
        startupTimeoutJob = CoroutineScope(Dispatchers.IO).launch {
            delay(START_TIMEOUT_MS)
            handleStartupTimeout()
        }
    }

    private fun cancelStartupTimeout() {
        startupTimeoutJob?.cancel()
        startupTimeoutJob = null
    }

    private fun handleStartupTimeout() {
        val previousState = endStartupSessionIfTimedOut() ?: return
        startupTimeoutJob = null
        cancelPendingPauseDisconnect()
        pendingStopShutdownJob?.cancel()
        pendingStopShutdownJob = null
        deferredResumeRequest.set(null)

        try {
            val loopStopped = audioRecorder.stopLoopAndRecording()
            audioRecorder.releaseIfLoopStopped(loopStopped)
            webSocketManager.shutdown()
        } catch (e: Exception) {
            asrListener.onSessionError(e.message ?: "Stop failed")
            Log.e(HPT_ASR_TAG, "Failed to cleanup after startup timeout", e)
        } finally {
            audioRecorder.abandonAudioFocus()
            pendingResumedReason.set(null)
            asrListener.onSessionError("ASR start timeout")
            asrListener.onStateChanged(AsrSessionState.ENDED, AsrStateReason.ERROR)
            Log.w(HPT_ASR_TAG, "ASR start timeout, previousState=$previousState")
        }
    }

    private fun endStartupSessionIfTimedOut(): EngineState? {
        while (true) {
            val current = engineState.get()
            if (current.phase != EnginePhase.CONNECTING || current.hasConnectedOnce) {
                return null
            }
            if (engineState.compareAndSet(current, EngineState())) {
                return current
            }
        }
    }

    /**
     * 只有仍处于 PAUSING 的会话才能落成最终 PAUSED，
     * 避免过期延迟任务把 stop() 之后的 IDLE 又改回暂停态。
     */
    private fun completePauseTransition(): Boolean {
        while (true) {
            val current = engineState.get()
            when (current.phase) {
                EnginePhase.PAUSING -> {
                    val updated = current.copy(phase = EnginePhase.PAUSED)
                    if (engineState.compareAndSet(current, updated)) {
                        return true
                    }
                }

                EnginePhase.PAUSED -> return true
                else -> return false
            }
        }
    }

    private fun finishPauseIfNeeded(reason: AsrStateReason) {
        if (!completePauseTransition()) {
            return
        }
        asrListener.onStateChanged(AsrSessionState.PAUSED, reason)
        Log.d(HPT_ASR_TAG, "Recording paused, reason=$reason")
        consumeDeferredResumeRequest()?.let { request ->
            resumeInternal(
                reason = request.reason,
                shouldRequestAudioFocus = request.shouldRequestAudioFocus,
            )
        }
    }

    private fun consumeDeferredResumeRequest(): DeferredResumeRequest? {
        return deferredResumeRequest.getAndSet(null)
    }

    private fun cancelPendingPauseDisconnect() {
        pendingPauseDisconnectJob?.cancel()
        pendingPauseDisconnectJob = null
    }

    /** 原子更新引擎状态，并返回更新后的值。 */
    private fun updateEngineState(transform: (EngineState) -> EngineState): EngineState {
        return updateEngineStateAndGetBeforeAfter(transform).second
    }

    /** 原子更新引擎状态，并同时拿到更新前后的快照。 */
    private fun updateEngineStateAndGetBeforeAfter(transform: (EngineState) -> EngineState): Pair<EngineState, EngineState> {
        while (true) {
            val current = engineState.get()
            val updated = transform(current)
            if (engineState.compareAndSet(current, updated)) {
                return current to updated
            }
        }
    }
}
