package com.igancao.hptasr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.gson.Gson
import com.igancao.hptasr.bean.AsrResult
import com.igancao.hptasr.bean.AudioInfo
import com.igancao.hptwebsocket.WebSocketManager
import com.igancao.hptwebsocket.WsConfig
import com.igancao.hptwebsocket.WsListener
import com.igancao.hptwebsocket.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean

class AsrEngine(
    private val audioInfo: AudioInfo,
    private val asrListener: AsrListener
) : WsListener {

    private var audioRecord: AudioRecord? = null
    private val webSocketManager = WebSocketManager()
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private val gson = Gson()
    private var taskId: String? = null

    // 维护已确定的分句历史
    private val finalSentences = StringBuilder()

    private val minBufferSize = AudioRecord.getMinBufferSize(
        audioInfo.rate,
        if (audioInfo.channel == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
        if (audioInfo.bits == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
    )

    /**
     * 启动 ASR 引擎
     * @param wsConfig WebSocket 配置，允许调用者自定义重连策略、日志等
     */
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    fun start(taskId: String, wsConfig: WsConfig = WsConfig()) {
        if (isRecording.compareAndSet(false, true)) {
            this.taskId = taskId
            isPaused.set(false)
            finalSentences.setLength(0) // 开启时清空历史
            try {
                // 初始化录音机
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    audioInfo.rate,
                    if (audioInfo.channel == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
                    if (audioInfo.bits == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT,
                    minBufferSize * 2
                )

                webSocketManager.init(wsConfig, this)
            } catch (e: Exception) {
                isRecording.set(false)
                asrListener.onError(e.message ?: "Initialization failed")
                Log.e(HPT_ASR_TAG, "Initialization failed", e)
            }
        } else {
            Log.w(HPT_ASR_TAG, "Already started")
        }
    }

    /**
     * 暂停
     * 没有在录制音频直接返回
     */
    fun pause() {
        if (!isRecording.get()) return

        try {
            audioRecord?.stop()
            isPaused.set(true)
            // 执行暂停逻辑
            webSocketManager.send(WsMessage.Text("PAUSE"))
            Log.d(HPT_ASR_TAG, "Recording paused")
        } catch (e: Exception) {
            Log.e(HPT_ASR_TAG, "Failed to pause recording", e)
        }
    }

    /**
     * 恢复
     * 没有在录制音频直接返回
     */
    fun resume() {
        if (!isRecording.get()) return

        try {
            // 执行继续逻辑 不需要发送resume 直接传数据
            // webSocketManager.send(WsMessage.Text("RESUME"))
            audioRecord?.startRecording()
            isPaused.set(false)
            Log.d(HPT_ASR_TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(HPT_ASR_TAG, "Failed to resume recording", e)
        }
    }

    fun stop() {
        if (isRecording.compareAndSet(true, false)) {
            isPaused.set(false)
            recordingJob?.cancel()
            recordingJob = null

            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                // Send end frame
                webSocketManager.send(WsMessage.Text("END"))
                webSocketManager.disconnect()
            } catch (e: Exception) {
                asrListener.onError(e.message ?: "Stop failed")
                Log.e(HPT_ASR_TAG, "Stop failed", e)
            } finally {
                finalSentences.setLength(0) // 停止时清空历史
            }
        }
    }

    /**
     * 在这里配置ws推流时需要的请求header
     */
    override fun buildHeaders(builder: Request.Builder, config: WsConfig) {
        super.buildHeaders(builder, config)
        builder.addHeader("token", "apptp1dd1227159")
        builder.addHeader("task_id", this.taskId ?: "")
    }

    override fun onOpen() {
        Log.i(HPT_ASR_TAG, "WebSocket opened")
        audioRecord?.startRecording()
        asrListener.onReady()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            recordAndSendAudioData()
        }
    }

    private suspend fun recordAndSendAudioData() {
        val buffer = ByteArray(audioInfo.chunkSize)
        while (isRecording.get()) {
            if (isPaused.get()) {
                delay(100) // 暂停期间减少循环频率
                continue
            }

            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                webSocketManager.send(WsMessage.Binary(buffer.copyOf(read)))
            }
        }
    }

    override fun onTextMessage(text: String) {
        Log.d(HPT_ASR_TAG, "Received: $text")
        try {
            val response = gson.fromJson(text, AsrResult::class.java)
            val resultData = response.payloadMsg?.result ?: return
            val utterances = resultData.utterances

            // 1. 兜底逻辑
            if (utterances.isNullOrEmpty()) {
                val fullText = finalSentences.toString() + resultData.text
                asrListener.onResult(fullText)
                Log.w(HPT_ASR_TAG, "此次回调onTextMessage没有收到分段信息utterances")
                return
            }

            // 2. 遍历分句逻辑
            var currentPreviewText = ""
            utterances.forEach { utterance ->
                if (utterance.definite) {
                    // 确定结果存入 StringBuilder
                    finalSentences.append(utterance.text)
                } else {
                    // 非确定结果作为当前预览
                    currentPreviewText = utterance.text
                }
            }

            // 3. 统一通过 onResult 回调：历史确定的内容 + 当前正在识别的预览
            val totalText = finalSentences.toString() + currentPreviewText
            asrListener.onResult(totalText)

        } catch (e: Exception) {
            Log.e(HPT_ASR_TAG, "Error parsing ASR message", e)
        }
    }

    override fun onFailure(t: Throwable) {
        isRecording.set(false)
        isPaused.set(false)
        asrListener.onError(t.message ?: "WebSocket failure")
        Log.e(HPT_ASR_TAG, "WebSocket failure", t)
    }
}
