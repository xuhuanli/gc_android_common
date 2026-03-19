package com.igancao.hpt_jsbridge

import android.util.Log
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.igancao.hpt_jsbridge.JsBridgeConfig.enableLog
import com.igancao.hpt_jsbridge.datas.BridgeRequest
import com.igancao.hpt_jsbridge.datas.BridgeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Copyright (c) 2026-02, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
open class AndroidBaseBridge(
    private val bridgeWebView: BridgeWebView,
    private val externalLogger: IBridgeLog? = null
) {
    // 存储所有注册进来的外部模块处理器
    private val handlers = mutableListOf<BridgeActionHandler>()

    // @JavascriptInterface发生在js线程 可以用这个协程切到主线程做些ui操作
    val bridgeScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    // 供外部业务模块注册自己的 Handler
    fun registerHandler(handler: BridgeActionHandler) {
        if (!handlers.contains(handler)) {
            handlers.add(handler)
        }
    }

    fun unregisterHandler(handler: BridgeActionHandler) {
        handlers.remove(handler)
    }

    fun destroy() {
        bridgeScope.cancel()
    }

    /**
     * 调用 Web 端的 onBridgeSysAck 方法，用于App收到请求后的立即确认。
     * SysAck大部分情况sdk都会默认调用完成
     *
     * @param traceId 请求的唯一标识id，需要回传
     * @param code 响应码 (0: 成功, -1: 协议未注册, -2: 传参错误, -99: 授权校验未通过)
     * @param message 响应消息，尤其是在失败时提供具体原因
     */
    protected fun invokeOnBridgeSysAck(traceId: String, code: Int, message: String) {
        // 1. 构建 BridgeResponse 对象
        val response = BridgeResponse(
            traceId = traceId,
            timestamp = System.currentTimeMillis(), // 使用当前系统时间作为时间戳
            code = code,
            message = message,
            data = null // SysAck不需要data
        )

        // 2. 将 BridgeResponse 对象序列化为 JSON 字符串
        val responseJson = JsParamsUtil.toJson(response)

        // 3. 构建并执行 JavaScript 调用
        val script = "javascript:window.onBridgeSysAck($responseJson)"
        // 通过抽象接口调用，而不是具体的 webView.post
        bridgeWebView.execJavascript(script)
    }

    /**
     * 调用 Web 端的 onBridgeBizResponse 方法，用于App处理完业务后的异步回调。
     * BizResponse sdk不会默认调用，需要自行在web侧需要的场景下手动调用
     * 1.
     * 对于同步操作：block 代码块走完就意味着业务完成了，可以立即调用 invokeOnBridgeBizResponse。
     * 2.
     * 对于异步操作：block 只是启动了异步任务，需要在异步任务的回调中调用 invokeOnBridgeBizResponse。
     *
     * @param traceId 请求的唯一标识id，需要回传
     * @param code 响应码，通常为 0 表示成功
     * @param message 响应消息
     * @param data 携带的业务数据，可以为 null
     */
    fun invokeOnBridgeBizResponse(
        traceId: String,
        code: Int,
        message: String,
        data: Any?
    ) {
        // 1. 构建 BridgeResponse 对象
        val response = BridgeResponse(
            traceId = traceId,
            timestamp = System.currentTimeMillis(),
            code = code,
            message = message,
            data = data // 将业务数据传入
        )

        // 2. 将 BridgeResponse 对象序列化为 JSON 字符串
        val responseJson = JsParamsUtil.toJson(response)

        // 3. 构建并执行 JavaScript 调用
        val script = "javascript:window.onBridgeBizResponse($responseJson)"
        // 抽象接口调用
        bridgeWebView.execJavascript(script)
    }

    /**
     * 统一执行入口：
     * 1) 先执行协议解析与 SysAck（parseBridgeRequestAndAck）
     * 2) 再分发给模块处理（dispatchToModules）
     * 3) 模块返回 syncReturn 时，直接作为 @JavascriptInterface 同步返回值
     * 4) 未命中模块时统一记录日志并回传 invokeOnBridgeSysAck(-1)
     *
     * @return 同步返回值；未提供 syncReturn 时返回 "{}"
     */
    protected fun parseAndDispatchToModules(
        methodName: String,
        bridgeRequest: String?
    ): String {
        var syncResult = "{}"
        parseBridgeRequestAndAck(methodName, bridgeRequest) { traceId, payload ->
            when (
                val result = dispatchToModules(
                    methodName = methodName,
                    traceId = traceId,
                    requestStr = bridgeRequest.orEmpty(),
                    payloadMap = payload
                )
            ) {
                is BridgeHandleResult.Handled -> {
                    syncResult = result.syncReturn ?: "{}"
                }
                BridgeHandleResult.NotHandled -> {
                    logJsInfo(
                        method = methodName,
                        param = bridgeRequest,
                        throwable = Throwable("No module handled this action")
                    )
                    if (traceId.isNotBlank()) {
                        invokeOnBridgeSysAck(traceId, -1, "No module handled this action")
                    }
                }
            }
        }
        return syncResult
    }

    /**
     * 将 JS 调用分发给外部模块处理。
     * 业务模块自行决定是否回传 BizResponse。
     *
     * 注意：只有返回 BridgeHandleResult.Handled 才会被视为“已处理”。
     * 如果模块实际执行了逻辑但误返回 NotHandled，最终会被判定为“未处理”。
     *
     * @return 首个模块处理结果；若都不处理则返回 NotHandled
     */
    private fun dispatchToModules(
        methodName: String,
        traceId: String,
        requestStr: String,
        payloadMap: Map<String, Any>
    ): BridgeHandleResult {
        for (handler in handlers) {
            when (val result = handler.handleAction(methodName, traceId, requestStr, payloadMap, this)) {
                is BridgeHandleResult.Handled -> return result
                BridgeHandleResult.NotHandled -> Unit
            }
        }
        return BridgeHandleResult.NotHandled
    }

    /**
     * 执行 JS 方法：
     * 1) 解析 BridgeRequest
     * 2) 严格校验/解析 payload
     * 3) 校验通过后发送 SysAck
     * 4) 回调业务 block（不做线程切换）
     *
     * @param methodName 方法名，通常由 @JavascriptInterface 注解的方法提供
     * @param bridgeRequest H5按照约定传入的、符合 BridgeRequest 结构的JSON字符串
     * @param block 将解析出的traceid和payload返回
     */
    private fun parseBridgeRequestAndAck(
        methodName: String,
        bridgeRequest: String?,
        block: (traceId: String, payload: Map<String, Any>) -> Unit
    ) {
        if (bridgeRequest.isNullOrBlank()) {
            // 如果js没有传参，也给一个ack响应
            // BridgeRequest isNullOrBlank 时 traceId固定000000
            invokeOnBridgeSysAck(
                "000000",
                -2,
                "传参错误: BridgeRequest isNullOrBlank for method '$methodName'"
            )
            // 同时打印错误日志
            logJsInfo(
                method = methodName,
                param = "",
                throwable = Throwable("传参错误: BridgeRequest isNullOrBlank for method '$methodName'")
            )
            return
        }

        try {
            logJsInfo(method = methodName, param = bridgeRequest)
            // 1. 将 JSON 字符串解析为 BridgeRequest 对象
            val request = JsParamsUtil.fromJson(bridgeRequest, BridgeRequest::class.java)
            val traceId = request.traceId

            // 2. 先校验/解析 payload，支持 null 或空字符串
            // payload 有内容时必须是 JSON 对象格式
            val payloadMap = parsePayloadToMap(payload = request.payload)

            // 3. request/payload 解析通过后，再发送系统确认 (SysAck)
            invokeOnBridgeSysAck(traceId, 0, "success")

            // 4. 执行具体的业务逻辑，传入 traceId 和解析后的 payloadMap
            block(traceId, payloadMap)

        } catch (e: Exception) {
            // JSON 解析失败或其它异常
            logJsInfo(
                method = methodName,
                param = bridgeRequest,
                throwable = Throwable("发生错误: 类型为 ${e::class.java.simpleName} for method '$methodName'. Error: ${e.message}")
            )
            // 尝试从原始JSON中提取traceId，以便给前端一个失败的交代
            val traceId = extractTraceIdFromJson(bridgeRequest)
            invokeOnBridgeSysAck(
                traceId,
                -2,
                "发生错误: 类型为 ${e::class.java.simpleName} for method '$methodName'. Error: ${e.message}"
            )
        }
    }

    @Throws(JsonSyntaxException::class)
    private fun parsePayloadToMap(
        payload: Any?
    ): Map<String, Any> {
        // payload 允许为 null 或空字符串（视为无业务参数）
        if (payload == null) return emptyMap()
        if (payload is String && payload.isBlank()) return emptyMap()

        // payload 有内容时必须是 JSON 对象
        val payloadJson = if (payload is String) payload else JsParamsUtil.toJson(payload)
        return JsParamsUtil.fromJsonToMap(payloadJson)
    }

    /**
     * 从一个JSON字符串中尝试提取 "traceid" 字段。
     * 用于在完整的BridgeRequest解析失败时，也能给前端一个交代。
     */
    private fun extractTraceIdFromJson(json: String): String {
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return "000000"
            val traceIdElement = root.asJsonObject.get("traceid")
            if (traceIdElement == null || traceIdElement.isJsonNull) "000000" else traceIdElement.asString
        } catch (e: Exception) {
            e.printStackTrace()
            "000000"
        }
    }

    private fun logJsInfo(
        method: String,
        param: String? = null,
        throwable: Throwable? = null
    ) {
        if (enableLog) {
            val thread = Thread.currentThread()
            Log.i(
                BRIDGE_LOG_TAG,
                "method=$method thread_name=${thread.name}" +
                        "\nparam: $param" +
                        "\nthrow:$throwable"
            )
        }
        externalLogger?.addLog(method, param, throwable)
    }
}
