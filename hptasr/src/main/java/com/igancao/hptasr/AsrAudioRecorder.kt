package com.igancao.hptasr

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.igancao.hptasr.bean.AudioInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.log10
import kotlin.math.sqrt

internal class AsrAudioRecorder(
    private val context: Context?,
    private val audioInfo: AudioInfo,
    private val callback: Callback,
) {
    interface Callback {
        fun isRecordingActive(): Boolean
        fun isSessionEnded(): Boolean
        fun onAudioData(data: ByteArray)
        fun onDecibelsChanged(db: Double)
        fun onRecordingDurationChanged(durationSeconds: Double)
        fun onAudioFocusLoss()
        fun onAudioFocusLossTransient()
        fun onAudioFocusGain()
    }

    private var audioRecord: AudioRecord? = null
    private val audioManager: AudioManager? = context?.getSystemService(AudioManager::class.java)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusGranted = false
    private var loopJob: Job? = null
    private var lastVolumeCallbackMs = 0L
    private var lastDurationCallbackMs = 0L
    private var recordingDurationSeconds = 0.0

    private val minBufferSize = AudioRecord.getMinBufferSize(
        audioInfo.rate,
        if (audioInfo.channel == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
        if (audioInfo.bits == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT,
    )

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(HPT_ASR_TAG, "onAudioFocusChange: AUDIOFOCUS_LOSS")
                callback.onAudioFocusLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(HPT_ASR_TAG, "onAudioFocusChange: AUDIOFOCUS_LOSS_TRANSIENT or AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                callback.onAudioFocusLossTransient()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(HPT_ASR_TAG, "onAudioFocusChange: AUDIOFOCUS_GAIN")
                callback.onAudioFocusGain()
            }
            else -> Log.d(HPT_ASR_TAG, "onAudioFocusChange: unknown focusChange=$focusChange")
        }
    }

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    fun initAudioRecord() {
        if (minBufferSize <= 0) {
            throw IllegalStateException(
                "Invalid AudioRecord buffer size=$minBufferSize, rate=${audioInfo.rate}, channel=${audioInfo.channel}, bits=${audioInfo.bits}"
            )
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            audioInfo.rate,
            if (audioInfo.channel == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
            if (audioInfo.bits == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT,
            minBufferSize * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException(
                "AudioRecord not initialized, rate=${audioInfo.rate}, channel=${audioInfo.channel}, bits=${audioInfo.bits}"
            )
        }
        audioRecord = recorder
    }

    fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .build()
                .also { audioFocusRequest = it }
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        audioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return audioFocusGranted
    }

    fun abandonAudioFocus() {
        if (!audioFocusGranted) return
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusListener)
        }
        audioFocusGranted = false
    }

    fun startRecordingIfNeeded() {
        val recorder = audioRecord ?: return
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord not initialized")
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.startRecording()
        }
    }

    fun stopRecordingIfNeeded() {
        val recorder = audioRecord ?: return
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            recorder.stop()
            dispatchCurrentDuration()
        }
    }

    fun releaseIfLoopStopped(loopStopped: Boolean) {
        releaseAudioRecord(force = loopStopped)
    }

    private fun releaseAudioRecord(force: Boolean) {
        val recorder = audioRecord ?: return
        if (force) {
            recorder.release()
        } else {
            Log.w(HPT_ASR_TAG, "Skip AudioRecord.release() because recorder loop is still running")
        }
        audioRecord = null
    }

    /**
     * 启动采集协程。在整次会话期间持续保活：
     * ACTIVE 时读取并上报音频数据，其他阶段仅空转等待，IDLE 时自然退出。
     */
    fun startLoop() {
        loopJob?.cancel()
        loopJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(audioInfo.chunkSize)
            while (isActive && !callback.isSessionEnded()) {
                if (!callback.isRecordingActive()) {
                    delay(100)
                    continue
                }
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    if (!isActive || callback.isSessionEnded()) {
                        break
                    }
                    if (!callback.isRecordingActive()) {
                        continue
                    }
                    val data = buffer.copyOf(read)
                    dispatchDurationIfNeeded(read)
                    dispatchVolumeIfNeeded(data)
                    callback.onAudioData(data)
                }
            }
        }
    }

    private fun dispatchDurationIfNeeded(byteCount: Int) {
        val bytesPerSample = if (audioInfo.bits == 16) 2.0 else 1.0
        val bytesPerSecond = audioInfo.rate.toDouble() *
            audioInfo.channel.coerceAtLeast(1) *
            bytesPerSample
        if (bytesPerSecond <= 0.0) return

        recordingDurationSeconds += byteCount / bytesPerSecond
        val now = System.currentTimeMillis()
        if (now - lastDurationCallbackMs < DURATION_CALLBACK_INTERVAL_MS) return
        dispatchCurrentDuration(now)
    }

    private fun dispatchCurrentDuration(now: Long = System.currentTimeMillis()) {
        lastDurationCallbackMs = now
        callback.onRecordingDurationChanged(recordingDurationSeconds)
    }

    private fun dispatchVolumeIfNeeded(data: ByteArray) {
        val now = System.currentTimeMillis()
        if (now - lastVolumeCallbackMs < VOLUME_CALLBACK_INTERVAL_MS) return
        lastVolumeCallbackMs = now

        val db = calculateDb(data)
        callback.onDecibelsChanged(db)
    }

    private fun calculateDb(data: ByteArray): Double {
        if (data.isEmpty()) return MIN_DB
        var sum = 0.0
        var samples = 0
        if (audioInfo.bits == 16) {
            var i = 0
            while (i + 1 < data.size) {
                val lo = data[i].toInt() and 0xFF
                val hi = data[i + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
                sum += sample * sample
                samples++
                i += 2
            }
        } else {
            for (byte in data) {
                val sample = ((byte.toInt() and 0xFF) - 128).toDouble() / 128.0
                sum += sample * sample
                samples++
            }
        }
        if (samples == 0) return MIN_DB
        val rms = sqrt(sum / samples)
        return if (rms > 0.0) (20.0 * log10(rms)).coerceIn(MIN_DB, 0.0) else MIN_DB
    }

    fun stopLoopAndRecording(): Boolean {
        val job = loopJob
        job?.cancel()
        var stopFailure: Throwable? = null
        try {
            stopRecordingIfNeeded()
        } catch (t: Throwable) {
            stopFailure = t
        }
        val loopStopped = waitForLoopToStop(job)
        stopFailure?.let { throw it }
        return loopStopped
    }

    private fun waitForLoopToStop(job: Job?): Boolean {
        if (job == null) return true
        val completed = runBlocking {
            withTimeoutOrNull(LOOP_STOP_TIMEOUT_MS) {
                job.join()
                true
            } ?: false
        }
        if (!completed) {
            Log.w(HPT_ASR_TAG, "Audio recorder loop did not stop within ${LOOP_STOP_TIMEOUT_MS}ms")
        }
        if (loopJob === job) {
            loopJob = null
        }
        return completed
    }

    companion object {
        private const val VOLUME_CALLBACK_INTERVAL_MS = 100L
        private const val DURATION_CALLBACK_INTERVAL_MS = 100L
        private const val LOOP_STOP_TIMEOUT_MS = 300L
        private const val MIN_DB = -120.0
    }
}
