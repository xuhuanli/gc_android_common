package com.igancao.hptwebsocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Copyright (c) 2025-11, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 * okHttpClient使用NormalOkHttpClient 里面的配置都是ci的参数
 */

class WebSocketManager(private val okHttpClient: OkHttpClient) : WebSocketListener() {

    private val TAG = "WebSocketManager"

    private var config: WsConfig? = null
    private var webSocket: WebSocket? = null

    // 语音识别平台
    private var wsListener: WsListener? = null

    private val isConnected = AtomicBoolean(false)
    private var reconnectAttempts = 0

    private var jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 队列保存连接建立前发送的消息
    private val messageQueue = LinkedBlockingQueue<WsMessage>(200)

    // 服务器的回复队列
    private val responseQueue = LinkedBlockingQueue<WsMessage>(200)

    companion object {
        const val MANUAL_CLOSE_CODE = 1000
    }


    /** ------------------ API ------------------ */

    fun init(config: WsConfig, listener: WsListener) {
        this.config = config
        this.wsListener = listener
        connect()
        parseLoop()
    }

    private fun parseLoop() {
        jobScope.launch {
            while (isActive) {
                val msg = responseQueue.poll(100, TimeUnit.MILLISECONDS)
                msg?.let {
                    when (msg) {
                        is WsMessage.Text -> {
                            wsListener?.onTextMessage(msg.text)
                        }

                        is WsMessage.Binary -> {
                            wsListener?.onBinaryMessage(msg.bytes)
                        }
                    }
                }
            }
        }
    }

    fun connect() {
        val cfg = config ?: return

        if (isConnected.get()) return

        val builder = Request.Builder()

        // 调用各平台实现 配置不同语音平台的连接参数
        wsListener?.buildHeaders(builder, cfg)

        val request = builder.build()

        webSocket = okHttpClient
            .newWebSocket(request, this)
        Log.d(TAG, "WebSocket 连接 --> ${cfg.url}")
    }

    /**
     * 手动关闭连接
     */
    fun disconnect() {
        reconnectAttempts = 0
        isConnected.set(false)
        webSocket?.close(MANUAL_CLOSE_CODE, "WebSocket 手动断开连接")
        Log.d(TAG, "WebSocket 手动断开连接")
    }

    /**
     * 发消息
     * Text:
     * { "cmd": "start" }
     * { "cmd": "end" }
     * Text: 心跳使用这个
     * Binary: 音频使用这个
     * [0x01, 0x02, 0x03, 0x04]
     */
    fun send(msg: WsMessage) {
        // 1. 先入队
        val success = messageQueue.offer(msg)
        if (!success) {
            handleSendQueueOverflow(msg)
            return
        }
        // 2. 如果已连接，启动队列发送
        flushQueue()
    }

    private fun flushQueue() {
        if (!isConnected.get()) return
        while (true) {
            val next = messageQueue.poll() ?: break
            when (next) {
                is WsMessage.Text -> {
                    webSocket?.let { ws ->
                        wsListener?.onSendText(ws, next.text)
                        ws.send(next.text)
                    }
                }

                is WsMessage.Binary -> {
                    webSocket?.let { ws ->
                        wsListener?.onSendBinary(ws, next.bytes)
                        ws.send(ByteString.of(*next.bytes))
                    }
                }
            }
        }
    }

    /** ------------------ WebSocket 回调 ------------------ */

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket 连接成功")
        isConnected.set(true)
        reconnectAttempts = 0
        wsListener?.onOpen()

        // 所有连接前入队的消息都按顺序发送
        flushQueue()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "===> 收到文本消息: $text")
        val msg = WsMessage.Text(text)
        val success = responseQueue.offer(msg)
        if (!success) {
            handleReceiveQueueOverflow(msg)
            return
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "===> 收到ByteArray消息 size: ${bytes.size}")
        val data = bytes.toByteArray()
        val msg = WsMessage.Binary(data)
        val success = responseQueue.offer(msg)
        if (!success) {
            handleReceiveQueueOverflow(msg)
            return
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        wsListener?.onClosing(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isConnected.set(false)
        wsListener?.onFailure(t)
        Log.e(TAG, "===> 连接失败: : ${t.message}")

        attemptReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isConnected.set(false)
        wsListener?.onClosed(code, reason)
        Log.d(TAG, "===> 连接已关闭: code=$code, reason=$reason")
        if (code != MANUAL_CLOSE_CODE) {
            attemptReconnect()
        }
    }

    /**
     * 尝试重连
     * 指数退避型重连时长直到到达reconnectMaxDelay重连时
     */
    private fun attemptReconnect() {
        val cfg = config ?: return
        if (isConnected.get()) return

        reconnectAttempts++
        // 指数退避
        var delayMillis = cfg.reconnectBaseDelay * (1 shl (reconnectAttempts - 1))
        // 最大延迟限制
        delayMillis = min(delayMillis, cfg.reconnectMaxDelay)

        jobScope.launch {
            Log.d(TAG, "等待${delayMillis}ms，第${reconnectAttempts}次尝试重连")
            delay(delayMillis)
            connect()
        }
    }

    private fun handleSendQueueOverflow(msg: WsMessage) {
        wsListener?.onSendQueueOverFlow(msg)
    }

    private fun handleReceiveQueueOverflow(msg: WsMessage) {
        wsListener?.onReceiveQueueOverFlow(msg)
    }

    /** ------------------ 手动停止此WebSocket连接 ------------------ */
    fun stopManual() {
        jobScope.cancel()
        disconnect()
    }
}