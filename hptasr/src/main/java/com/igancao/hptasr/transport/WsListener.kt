package com.igancao.hptasr.transport

import okhttp3.WebSocket

internal interface WsListener {
    fun onSendText(webSocket: WebSocket, text: String) {}
    fun onSendBinary(webSocket: WebSocket, data: ByteArray) {}
    fun onTextMessage(text: String) {}
    fun onBinaryMessage(bytes: ByteArray) {}
    fun onClosing(code: Int, reason: String) {}
    fun onClosed(code: Int, reason: String) {}
    fun onFailure(t: Throwable, code: Int? = null) {}
    fun onOpen() {}
    fun onSendQueueOverFlow(msg: WsMessage) {}
    fun onReceiveQueueOverFlow(msg: WsMessage) {}
}
