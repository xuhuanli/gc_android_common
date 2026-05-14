package com.igancao.hptasr.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import com.igancao.hptasr.HptAsrSystemConfig
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private const val TAG = "WebSocketManager"
private const val RECONNECT_BASE_DELAY_MS = 1000L
private const val RECONNECT_MAX_DELAY_MS = 60_000L
private const val ENABLE_LOG = true

internal class WebSocketManager(private val okHttpClient: OkHttpClient = createDefaultClient()) :
    WebSocketListener() {

    private var url: String = ""
    private var taskId: String = ""
    // 当前仍被认为有效的连接。只有它的回调才能改写状态。
    private val activeSocket = AtomicReference<WebSocket?>(null)

    private var wsListener: WsListener? = null
    private val isConnected = AtomicBoolean(false)
    private val isFlushing = AtomicBoolean(false)
    private val manualDisconnectRequested = AtomicBoolean(false)
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var parseJob: Job? = null

    private var jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 发送队列：连接建立前的消息先入队，发送失败时重新放回队首。
    private val messageQueue = LinkedBlockingDeque<WsMessage>(200)
    // 接收队列：把 ws 回调和上层解析解耦，避免直接在 OkHttp 线程里处理业务。
    private val responseQueue = LinkedBlockingQueue<WsMessage>(200)
    // 连续发送失败计数，超过阈值时清空队列并主动重连（对齐 iOS consecutiveSendFailures 策略）。
    private var consecutiveSendFailures = 0
    // 期望的下一条 payload_sequence，从 1 起。每次 onOpen 时重置：服务端在每个新 WS 连接上 seq 都从 1 重新开始
    // （pause/resume、自动重连均如此），客户端必须同步重置否则会把新连接的早期帧当作过时包丢弃。
    private var expectedSeq: Int = 1
    // seq 超前到达的临时缓冲，命中 expectedSeq 后按序排空。
    private val reorderBuffer = HashMap<Int, String>()
    // 保护 expectedSeq + reorderBuffer：onMessage 在 OkHttp 线程，init/disconnect 在业务线程。
    private val reorderLock = Any()
    private val enableLog: Boolean = ENABLE_LOG

    companion object {
        const val MANUAL_CLOSE_CODE = 1000
        private const val MAX_CONSECUTIVE_SEND_FAILURES = 6
        private const val MAX_REORDER_BUFFER_SIZE = 50

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .pingInterval(5, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()
        }
    }

    fun init(url: String, listener: WsListener, taskId: String) {
        this.url = url
        this.taskId = taskId
        this.wsListener = listener
        manualDisconnectRequested.set(false)
        ensureJobScope()
        connect()
        startParseLoopIfNeeded()
    }

//    fun getOkHttpClient() = okHttpClient

    // responseQueue 解析协程跟随 WebSocketManager 生命周期存在，避免每次 init 都重复启动一个常驻循环。
    private fun startParseLoopIfNeeded() {
        if (parseJob?.isActive == true) {
            return
        }
        parseJob = jobScope.launch {
            while (isActive) {
                val msg = responseQueue.poll(100, TimeUnit.MILLISECONDS)
                msg?.let {
                    when (msg) {
                        is WsMessage.Text -> wsListener?.onTextMessage(msg.text)
                        is WsMessage.Binary -> wsListener?.onBinaryMessage(msg.bytes)
                    }
                }
            }
        }
    }

    fun connect() {
        if (url.isBlank()) return

        if (isConnected.get()) return
        reconnectJob?.cancel()
        reconnectJob = null

        // 建新连接前先让旧连接失效。旧连接晚到的回调会因为不再是 activeSocket
        // 而被忽略，避免把当前连接状态改坏。
        activeSocket.getAndSet(null)?.cancel()

        val token = HptAsrSystemConfig.identifier
        val ticket = HptAsrSystemConfig.ticket
        val typeItemCode = HptAsrSystemConfig.typeItemCode

        val builder = Request.Builder()
            .url(url)
        if (token.isNotBlank()) {
            builder.addHeader("token", token)
        }
        builder.addHeader("task_id", taskId)
        if (ticket.isNotBlank()) {
            builder.addHeader("ticket", ticket)
        }
        if (typeItemCode.isNotBlank()) {
            builder.addHeader("type_item_code", typeItemCode)
        }

        val request = builder.build()
        if (enableLog) Log.d(TAG, "WebSocket connect headers -> ${request.headers}")
        val socket = okHttpClient.newWebSocket(request, this)
        activeSocket.set(socket)

        if (enableLog) Log.d(TAG, "WebSocket connect -> $url")
    }

    fun disconnect() {
        manualDisconnectRequested.set(true)
        reconnectAttempts = 0
        consecutiveSendFailures = 0
        reconnectJob?.cancel()
        reconnectJob = null
        activeSocket.getAndSet(null)?.close(MANUAL_CLOSE_CODE, "manual disconnect")
        isFlushing.set(false)
        isConnected.set(false)
        messageQueue.clear()
        responseQueue.clear()
        if (enableLog) Log.d(TAG, "WebSocket manually disconnected")
    }

    // 会话彻底结束时调用，除了断开连接，还要回收常驻协程，避免 WebSocketManager 被后台 job 持有。
    fun shutdown() {
        disconnect()
        parseJob?.cancel()
        parseJob = null
        jobScope.coroutineContext[Job]?.cancel()
        wsListener = null
        url = ""
        // 与 iOS HPTSocketManager.stopAndSocketStr: 一致：仅在会话级停止时重置，自动重连/pause 不重置。
        synchronized(reorderLock) {
            expectedSeq = 1
            reorderBuffer.clear()
        }
        if (enableLog) Log.d(TAG, "WebSocket manager shutdown")
    }

    fun reconnectNow() {
        manualDisconnectRequested.set(false)
        reconnectAttempts = 0
        if (isConnected.get()) return
        reconnectJob?.cancel()
        reconnectJob = null
        connect()
    }

    fun send(msg: WsMessage) {
        val success = messageQueue.offer(msg)
        if (!success) {
            handleSendQueueOverflow(msg)
        } else if (isConnected.get()) {
            flushQueue()
        }
    }

    private fun flushQueue() {
        if (!isConnected.get()) return
        if (!isFlushing.compareAndSet(false, true)) return
        if (enableLog) Log.i(TAG, "flushQueue: start sender loop")

        jobScope.launch {
            try {
                while (isConnected.get() && isActive) {
                    val next = messageQueue.poll()
                    if (next == null) {
                        delay(100)
                        continue
                    }

                    val socket = activeSocket.get()
                    val success = when (next) {
                        is WsMessage.Text -> socket?.send(next.text) ?: false
                        is WsMessage.Binary -> socket?.send(ByteString.of(*next.bytes)) ?: false
                    }

                    if (!success) {
                        consecutiveSendFailures++
                        if (consecutiveSendFailures > MAX_CONSECUTIVE_SEND_FAILURES) {
                            // 对齐 iOS 策略：连续失败超阈值时清空积压帧并主动重连
                            if (enableLog) Log.w(TAG, "Consecutive send failures=$consecutiveSendFailures, clearing queue and reconnecting")
                            messageQueue.clear()
                            consecutiveSendFailures = 0
                            reconnectNow()
                        } else {
                            if (enableLog) Log.e(TAG, "Send failed ($consecutiveSendFailures), requeue message at head")
                            if (isFlushing.get()) messageQueue.offerFirst(next)
                        }
                        break
                    } else {
                        consecutiveSendFailures = 0
                        if (socket != null) {
                            if (next is WsMessage.Text) wsListener?.onSendText(socket, next.text)
                            else if (next is WsMessage.Binary) wsListener?.onSendBinary(socket, next.bytes)
                        }
                    }
                }
            } catch (e: Exception) {
                if (enableLog) Log.e(TAG, "flushQueue error", e)
            } finally {
                isFlushing.set(false)
                if (enableLog) Log.d(TAG, "flushQueue: stop sender loop")
            }
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (!isActiveSocket(webSocket)) {
            if (enableLog) Log.d(TAG, "Ignore onOpen from stale socket")
            return
        }
        if (enableLog) Log.d(TAG, "WebSocket connected")
        isConnected.set(true)
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        // 服务端在每个新连接上 payload_sequence 从 1 起，本地必须同步重置；
        // 旧连接 reorderBuffer 中的高序号帧不会再到达，一并清空。
        synchronized(reorderLock) {
            expectedSeq = 1
            reorderBuffer.clear()
        }
        wsListener?.onOpen()
        flushQueue()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (!isActiveSocket(webSocket)) {
            if (enableLog) Log.d(TAG, "Ignore text message from stale socket")
            return
        }
        if (enableLog) Log.d(TAG, "Receive text message: $text")

        val seq: Int = try {
            val json = org.json.JSONObject(text)
            if (!json.has("payload_sequence")) {
                offerTextToResponseQueue(text)
                return
            }
            json.optInt("payload_sequence", 0)
        } catch (_: Exception) {
            // 非 JSON 文本：绕过排序直接派发（对齐 iOS HPTSocketManager.m 解析失败 fallback）
            offerTextToResponseQueue(text)
            return
        }

        synchronized(reorderLock) {
            when {
                seq == expectedSeq -> {
                    offerTextToResponseQueue(text)
                    expectedSeq++
                    drainReorderBufferLocked()
                }
                seq > expectedSeq -> {
                    reorderBuffer[seq] = text
                    if (reorderBuffer.size > MAX_REORDER_BUFFER_SIZE) {
                        if (enableLog) Log.w(TAG, "Reorder buffer overflow, skip expectedSeq=$expectedSeq")
                        expectedSeq++
                        drainReorderBufferLocked()
                    }
                }
                else -> {
                    if (enableLog) Log.d(TAG, "Drop stale/duplicate text: seq=$seq, expected=$expectedSeq")
                }
            }
        }
    }

    private fun offerTextToResponseQueue(text: String) {
        val msg = WsMessage.Text(text)
        val success = responseQueue.offer(msg)
        if (!success) {
            handleReceiveQueueOverflow(msg)
        }
    }

    private fun drainReorderBufferLocked() {
        while (reorderBuffer.containsKey(expectedSeq)) {
            val pending = reorderBuffer.remove(expectedSeq)!!
            offerTextToResponseQueue(pending)
            expectedSeq++
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        if (!isActiveSocket(webSocket)) {
            if (enableLog) Log.d(TAG, "Ignore binary message from stale socket")
            return
        }
        if (enableLog) Log.d(TAG, "Receive binary message size=${bytes.size}")
        val data = bytes.toByteArray()
        val msg = WsMessage.Binary(data)
        val success = responseQueue.offer(msg)
        if (!success) {
            handleReceiveQueueOverflow(msg)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        if (!isActiveSocket(webSocket)) {
            if (enableLog) Log.d(TAG, "Ignore onClosing from stale socket")
            return
        }
        wsListener?.onClosing(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (!isActiveSocket(webSocket)) {
            if (enableLog) Log.d(TAG, "Ignore onFailure from stale socket")
            return
        }
        activeSocket.compareAndSet(webSocket, null)
        isConnected.set(false)
        isFlushing.set(false)
        wsListener?.onFailure(t, response?.code)
        if (enableLog) Log.e(TAG, "WebSocket failure", t)
        if (!manualDisconnectRequested.get()) {
            attemptReconnect()
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (!isActiveSocket(webSocket)) {
            if (enableLog) Log.d(TAG, "Ignore onClosed from stale socket")
            return
        }
        activeSocket.compareAndSet(webSocket, null)
        isConnected.set(false)
        isFlushing.set(false)
        wsListener?.onClosed(code, reason)
        if (enableLog) Log.d(TAG, "WebSocket closed code=$code, reason=$reason")
        if (code != MANUAL_CLOSE_CODE && !manualDisconnectRequested.get()) {
            attemptReconnect()
        }
    }

    private fun attemptReconnect() {
        if (isConnected.get()) return
        if (manualDisconnectRequested.get()) return
        if (reconnectJob?.isActive == true) return

        reconnectAttempts++
        val exponent = (reconnectAttempts - 1).coerceAtMost(10)
        val delayMillis = min(RECONNECT_BASE_DELAY_MS * (1L shl exponent), RECONNECT_MAX_DELAY_MS)

        reconnectJob = jobScope.launch {
            if (enableLog) Log.d(TAG, "Reconnect in ${delayMillis}ms, attempt=$reconnectAttempts")
            delay(delayMillis)
            if (!manualDisconnectRequested.get()) {
                connect()
            }
        }
    }

    private fun ensureJobScope() {
        val scopeJob = jobScope.coroutineContext[Job]
        if (scopeJob == null || !scopeJob.isActive) {
            jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    private fun handleSendQueueOverflow(msg: WsMessage) {
        // 对齐 iOS RingBuffer 覆盖语义：丢弃最旧的帧，保留最新的帧
        messageQueue.pollFirst()
        messageQueue.offer(msg)
        if (enableLog) Log.w(TAG, "Send queue overflow, dropped oldest frame")
        wsListener?.onSendQueueOverFlow(msg)
    }

    private fun handleReceiveQueueOverflow(msg: WsMessage) {
        wsListener?.onReceiveQueueOverFlow(msg)
    }

    private fun isActiveSocket(socket: WebSocket): Boolean {
        return activeSocket.get() === socket
    }
}
