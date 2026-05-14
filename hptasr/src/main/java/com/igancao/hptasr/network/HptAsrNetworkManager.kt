package com.igancao.hptasr.network

import android.util.Log
import com.igancao.hptasr.HptAsrSystemConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 网络管理单例，负责 HTTP REST API 调用，对齐 iOS HPTASRNetworkManager。
 * 使用原生 org.json.JSONObject 解析 JSON，不依赖 Gson。
 */
object HptAsrNetworkManager {

    private const val TAG = "HptAsrNetwork"

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 缓存 acquireConfig 返回的原始 data JSON，对齐 iOS self.dataDic，直接透传给 createTask。 */
    @Volatile
    private var lastDataJson: JSONObject? = null

    /**
     * 获取最后一次成功获取的配置 JSON，对齐 iOS self.dataDic。
     */
    fun getLastDataJson(): JSONObject? = lastDataJson

    /**
     * 获取基础配置（采样率、声道数等音频参数），对齐 iOS acquireConfig:sourcePlatform:createTask:。
     *
     * @param autoCreateTask 获取配置成功后是否自动创建任务
     * @param sourcePlatform 来源平台标识，作为创建任务接口 body 参数 source_platform；null 时不携带
     * @param onResult 回调，(configJson, taskId, error)。configJson 为原始 data JSONObject，对齐 iOS NSDictionary；
     *                 若 autoCreateTask 为 true 且成功，则同时返回 taskId；否则 taskId 为 null。
     *                 error 不为 null 时表示请求失败，业务方可通过其 message 区分具体原因。
     */
    fun acquireConfig(
        autoCreateTask: Boolean = false,
        sourcePlatform: String? = null,
        onResult: (JSONObject?, String?, Throwable?) -> Unit,
    ) {
        if (!HptAsrSystemConfig.isValid()) {
            ioScope.launch {
                withContext(Dispatchers.Main) {
                    onResult(null, null, IllegalStateException("HptAsrSystemConfig not valid"))
                }
            }
            return
        }

        ioScope.launch {
            var configJson: JSONObject? = null
            var taskId: String? = null
            var error: Throwable? = null

            try {
                configJson = post(
                    url = "/api/v1/task/get_base_config",
                    fields = mapOf("third_party" to HptAsrSystemConfig.thirdParty),
                )
                lastDataJson = configJson

                if (configJson != null && autoCreateTask) {
                    val (id, err) = createTaskInternal(sourcePlatform)
                    taskId = id
                    error = err
                }
            } catch (e: Exception) {
                error = e
            }

            withContext(Dispatchers.Main) {
                onResult(configJson, taskId, error)
            }
        }
    }

    /**
     * 单独创建任务，依赖最后一次成功获取的配置 JSON 缓存。
     *
     * @param sourcePlatform 来源平台标识；null 时不携带 source_platform 字段
     * @param onResult 回调，(taskId, error)。error 不为 null 时表示请求失败。
     */
    fun createTask(
        sourcePlatform: String? = null,
        onResult: (String?, Throwable?) -> Unit,
    ) {
        if (lastDataJson == null || !HptAsrSystemConfig.isValid()) {
            onResult(null, IllegalStateException("lastDataJson is null or HptAsrSystemConfig not valid"))
            return
        }

        ioScope.launch {
            val (taskId, error) = try {
                createTaskInternal(sourcePlatform)
            } catch (e: Exception) {
                null to e
            }

            withContext(Dispatchers.Main) {
                onResult(taskId, error)
            }
        }
    }

    /**
     * 获取任务详情，返回原始 Map（对齐 iOS NSDictionary）。
     *
     * @param onResult 回调，(dataMap, error)。error 不为 null 时表示请求失败。
     */
    fun acquireTaskDetail(taskId: String, onResult: (Map<String, Any>?, Throwable?) -> Unit) {
        if (taskId.isBlank() || !HptAsrSystemConfig.isValid()) {
            onResult(null, IllegalStateException("taskId is blank or HptAsrSystemConfig not valid"))
            return
        }

        ioScope.launch {
            val (map, error) = try {
                val data = post(
                    url = "/api/v1/task/get",
                    fields = mapOf("task_id" to taskId),
                )
                val m = data?.let { jsonToMap(it) }
                m to null
            } catch (e: Exception) {
                null to e
            }

            withContext(Dispatchers.Main) {
                onResult(map, error)
            }
        }
    }

    // ---------- internal ----------

    private fun createTaskInternal(sourcePlatform: String?): Pair<String?, Throwable?> {
        val dataJson = lastDataJson ?: return null to IllegalStateException("lastDataJson is null")
        val fields = jsonToFormFields(dataJson).toMutableMap()
        if (!sourcePlatform.isNullOrEmpty()) {
            fields["source_platform"] = sourcePlatform
        }
        val data = post(
            url = "/api/v1/task/create",
            fields = fields,
        ) ?: return null to null
        return data.optString("task_id").takeIf { it.isNotEmpty() } to null
    }

    private fun post(url: String, fields: Map<String, String>): JSONObject? {
        val base = HptAsrSystemConfig.baseUrl.trimEnd('/')
        val fullUrl = "$base$url"
        val token = HptAsrSystemConfig.identifier
        val formBody = FormBody.Builder().apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()

        val requestBuilder = Request.Builder()
            .url(fullUrl)
        if (token.isNotBlank()) {
            requestBuilder.addHeader("token", token)
        }
        if (HptAsrSystemConfig.ticket.isNotBlank()) {
            requestBuilder.addHeader("ticket", HptAsrSystemConfig.ticket)
        }
        if (HptAsrSystemConfig.typeItemCode.isNotBlank()) {
            requestBuilder.addHeader("type_item_code", HptAsrSystemConfig.typeItemCode)
        }
        val request = requestBuilder.post(formBody).build()

        Log.d(TAG, "POST -> $fullUrl")
        request.headers.forEach { (name, value) ->
            Log.d(TAG, "  header $name=$value")
        }
        Log.d(TAG, "  fields=$fields")

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "POST <- $fullUrl HTTP ${response.code}")
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val bodyString = response.body?.string()
                    ?: throw IllegalStateException("Empty response body")
                Log.d(TAG, "POST <- $fullUrl body=$bodyString")
                val json = JSONObject(bodyString)
                val code = json.optInt("code", -1)
                if (code != 0 && code != 200) {
                    val msg = json.optString("message").takeIf { it.isNotEmpty() }
                        ?: json.optString("msg")
                    Log.e(TAG, "POST <- $fullUrl business error code=$code message=$msg")
                    throw IllegalStateException("Server error: code=$code, message=$msg")
                }
                json.optJSONObject("data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $fullUrl failed: ${e.message}")
            throw e
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonToMap(value)
                else -> value
            }
        }
        return map
    }

    private fun jsonToFormFields(json: JSONObject): Map<String, String> {
        val map = linkedMapOf<String, String>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> value.toString()
                else -> value.toString()
            }
        }
        return map
    }
}
