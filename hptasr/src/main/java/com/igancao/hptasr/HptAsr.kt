package com.igancao.hptasr

import android.util.Log
import androidx.annotation.RequiresPermission
import com.igancao.hptasr.bean.AudioInfo
import com.igancao.hptasr.bean.ConfigInfo
import com.igancao.hptasr.net.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * HptAsr SDK 入口类
 */

internal const val HPT_ASR_TAG = "HptAsr"

object HptAsr {

    // 缓存配置信息
    private var configInfo: ConfigInfo? = null

    private var asrEngine: AsrEngine? = null

    private var taskId: String? = null

    /**
     * SDK 初始化方法
     * 初始化会自动调用base_config接口获取配置
     */
    fun init(thirdParty: String, callback: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Retrofit 会在内部处理 Dispatchers.IO
                val response = RetrofitClient.apiService.getBaseConfig(thirdParty)
                Log.d(HPT_ASR_TAG, "Initializing HptAsr, config...${response.data}")
                if (response.code == 200 || response.code == 0) {
                    configInfo = response.data
                    // 确保 third_party 被正确设置，如果接口没返回则使用传入的
                    if (configInfo?.thirdParty.isNullOrEmpty()) {
                        configInfo?.thirdParty = thirdParty
                    }
                    Log.d(HPT_ASR_TAG, "Config fetched successfully: $configInfo")
                    callback?.invoke(true)
                } else {
                    Log.e(HPT_ASR_TAG, "Init error: ${response.message} (code: ${response.code})")
                    callback?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(HPT_ASR_TAG, "Init error: ${e.message}", e)
                callback?.invoke(false)
            }
        }
    }

    /**
     * 创建asr任务
     * 会自动调用create_task接口生成任务id
     */
    fun createAsrTask(callback: ((taskId: String?) -> Unit)? = null) {
        val thirdParty = configInfo?.thirdParty
        if (thirdParty.isNullOrEmpty()) {
            Log.e(HPT_ASR_TAG, "createAsrTask 失败: thirdParty 为空. 请先调用init方法")
            callback?.invoke(null)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = RetrofitClient.apiService.createTask(thirdParty)
                if (response.code == 200 || response.code == 0) {
                    Log.d(HPT_ASR_TAG, "Task created successfully: ${response.data}")
                    callback?.invoke(response.data?.taskId)
                } else {
                    Log.e(
                        HPT_ASR_TAG,
                        "Create task error: ${response.message} (code: ${response.code})"
                    )
                    callback?.invoke(null)
                }
            } catch (e: Exception) {
                Log.e(HPT_ASR_TAG, "Create task error: ${e.message}", e)
                callback?.invoke(null)
            }
        }
    }

    fun finishAsrTask() {

    }

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    fun startAsr(config: ConfigInfo, asrListener: AsrListener) {
        // 优化：处理可能的类型转换并从 config 获取参数
        val audioInfo = AudioInfo(
            format = "pcm",
            rate = config.audioMeta?.rate ?: 16000,
            channel = config.audioMeta?.channel ?: 1,
            bits = config.audioMeta?.bits ?: 16,
            duration = config.audioMeta?.duration ?: 200,
            chunkSize = config.audioMeta?.chunkSize ?: 3200
        )
        asrEngine = AsrEngine(audioInfo = audioInfo, asrListener = asrListener)
        createAsrTask { taskId ->
            if (!taskId.isNullOrEmpty()) {
                this.taskId = taskId
                // 开启asr引擎
                asrEngine?.start(taskId = taskId)
            } else {
                asrListener.onError("创建任务失败")
            }
        }
    }

    /**
     * 暂停录音
     */
    fun pauseAsr() {
        asrEngine?.pause()
    }

    fun resumeAsr() {
        asrEngine?.resume()
    }

    /**
     * 停止录音
     */
    fun stopAsr() {
        asrEngine?.stop()
    }

    /**
     * 获取缓存的配置
     */
    fun getConfigInfo(): ConfigInfo? = configInfo
}
