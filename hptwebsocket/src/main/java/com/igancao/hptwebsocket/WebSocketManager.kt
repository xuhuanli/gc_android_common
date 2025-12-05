package com.igancao.hptwebsocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.LinkedBlockingDeque
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

    // 队列保存连接建立前发送的消息，双端队列，需要把失败的消息放到首位，一直发送它直到成功
    private val messageQueue = LinkedBlockingDeque<WsMessage>(200)

    // 服务器的回复队列
    private val responseQueue = LinkedBlockingQueue<WsMessage>(200)
    var enableLog: Boolean = true

    companion object {
        const val MANUAL_CLOSE_CODE = 1000
    }


    /** ------------------ API ------------------ */

    fun init(config: WsConfig, listener: WsListener) {
        this.config = config
        this.wsListener = listener
        enableLog = this.config?.enableLog?: true
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
        if (enableLog) Log.d(TAG, "WebSocket 连接 --> ${cfg.url}")
    }

    /**
     * 手动关闭连接
     */
    fun disconnect() {
        reconnectAttempts = 0
        // 1. 停止 WebSocket，不触发重连
        webSocket?.close(MANUAL_CLOSE_CODE, "WebSocket 手动断开连接")
        // 4. 停止当前的 flush 协程循环
        isFlushing.set(false)
        // 5. 重置连接状态
        isConnected.set(false)
        // 清空队列
        messageQueue.clear()
        responseQueue.clear()
        if (enableLog) Log.d(TAG, "手动停止 WebSocket，不再重连，仅清空队列")
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
        jobScope.launch(Dispatchers.Main) {
            // 1. 尝试入队
            val success = messageQueue.offer(msg)
            if (!success) {
                handleSendQueueOverflow(msg)
                // 不调用 flushQueue，也不继续
            } else {
                // 2. 延迟，避免消息入队一下子太多导致超过最大值被丢弃。默认入队间隔是发送间隔的1/2
                delay((config?.sendInterval ?: 200) / 2)
                // 3. 如果已连接，启动队列发送
                if (isConnected.get()) {
                    flushQueue()
                }
            }
        }
    }

    // 是否有协程正在发送队列
    private val isFlushing = AtomicBoolean(false)

    private fun flushQueue() {
        if (enableLog) Log.i(TAG, "flushQueue: isConnected: ${isConnected.get()}, isFlushing: ${isFlushing.get()}")
        if (!isConnected.get()) return
        // 如果已有协程在发送，直接返回
        if (!isFlushing.compareAndSet(false, true)) return

        jobScope.launch {
            try {
                while (isConnected.get()) {
                    val next = messageQueue.poll() ?: break

                    delay(config?.sendInterval ?: 200) // 控制发送速率

                    val success = when (next) {
                        is WsMessage.Text -> webSocket?.send(next.text) ?: false
                        is WsMessage.Binary -> webSocket?.send(ByteString.of(*next.bytes)) ?: false
                    }

                    if (!success) {
                        // 只在写数据时遇到发送失败会回队列，如果是手动关闭的连接，为保证队列清空，这个延迟发送的msg不会归队
                        if (isFlushing.get()) {
                            Log.e(TAG, "发送失败，重新入队到队首")
                            messageQueue.offerFirst(next) // 放回队首
                        }
                        break // 停止当前 flush，等待下次 flush
                    } else {
                        // 回调
                        if (next is WsMessage.Text) wsListener?.onSendText(webSocket!!, next.text)
                        else if (next is WsMessage.Binary) wsListener?.onSendBinary(
                            webSocket!!,
                            next.bytes
                        )
                    }
                }
            } catch (e: Exception) {
                if (enableLog) Log.e(TAG, "flushQueue: error $e")
            } finally {
                isFlushing.set(false)
            }
        }
    }

    /** ------------------ WebSocket 回调 ------------------ */

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (enableLog) Log.d(TAG, "WebSocket 连接成功")
        isConnected.set(true)
        reconnectAttempts = 0
        wsListener?.onOpen()

        // 所有连接前入队的消息都按顺序发送
        flushQueue()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (enableLog) Log.d(TAG, "===> 收到文本消息: $text")
        val msg = WsMessage.Text(text)
        val success = responseQueue.offer(msg)
        if (!success) {
            handleReceiveQueueOverflow(msg)
            return
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        if (enableLog) Log.d(TAG, "===> 收到ByteArray消息 size: ${bytes}")
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
        if (enableLog) Log.e(TAG, "===> 连接失败: ", t)
        attemptReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isConnected.set(false)
        wsListener?.onClosed(code, reason)
        if (enableLog) Log.d(TAG, "===> 连接已关闭: code=$code, reason=$reason")
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
            if (enableLog) Log.d(TAG, "等待${delayMillis}ms，第${reconnectAttempts}次尝试重连")
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
}