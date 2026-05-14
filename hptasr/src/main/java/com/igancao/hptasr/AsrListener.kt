package com.igancao.hptasr

enum class AsrSessionState {
    READY,
    PAUSED,
    RESUMED,
    ENDED,
}

enum class AsrStateReason(val wireValue: String) {
    MANUAL("manual"),
    AUDIO_FOCUS_LOSS("audio_focus_loss"),
    AUDIO_FOCUS_LOSS_TRANSIENT("audio_focus_loss_transient"),
    AUDIO_FOCUS_GAIN("audio_focus_gain"),
    NORMAL_STOP("normal_stop"),
    ERROR("error"),
    APP_TASK_REMOVED("app_task_removed"),
}

/**
 * ASR callback interface. Callbacks are not guaranteed to be invoked on the main thread.
 */
interface AsrListener {
    fun onReady()

    fun onWsMessage(text: String)

    fun onSessionError(error: String)

    fun onDecibelsChanged(db: Double) = Unit

    fun onRecordingDurationChanged(durationSeconds: Double) = Unit

    fun onWsOpen() = Unit

    fun onWsClosed(code: Int, reason: String) = Unit

    fun onWsFailure(error: String, code: Int?) = Unit

    fun onStateChanged(state: AsrSessionState, reason: AsrStateReason) = Unit
}
