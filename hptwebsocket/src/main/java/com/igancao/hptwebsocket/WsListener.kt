package com.igancao.hptwebsocket

import okhttp3.Request
import okhttp3.WebSocket

/**
 * Copyright (c) 2025-11, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
interface WsListener {
    /**
     * 配置不同语音平台的连接参数
     * @param builder 请求构建器
     * @param config 配置
     */
    fun buildHeaders(builder: Request.Builder, config: WsConfig) {}

    // 发送文本之前的回调
    fun onSendText(webSocket: WebSocket, text: String) {}

    // 发送二进制之前的回调
    fun onSendBinary(webSocket: WebSocket, data: ByteArray) {}

    /**
     * 收到服务器发送的文本消息时调用
     */
    fun onTextMessage(text: String) {}

    /**
     * 收到服务器发送的二进制消息时调用
     */
    fun onBinaryMessage(bytes: ByteArray) {}

    /**
     * 服务器即将关闭连接
     */
    fun onClosing(code: Int, reason: String) {}

    /**
     * 两端都关闭，连接已经完全断开
     */
    fun onClosed(code: Int, reason: String) {}

    /**
     * 连接失败 / 读写异常 / 网络错误 / 服务器异常引起的关闭
     */
    fun onFailure(t: Throwable) {}

    /**
     * WebSocket 连接成功建立时调用。
     * 此时客户端和服务器已经完成握手
     */
    fun onOpen() {}

    /**
     * 发送消息队列溢出
     */
    fun onSendQueueOverFlow(msg: WsMessage) {}

    /**
     * 接收消息队列溢出
     */
    fun onReceiveQueueOverFlow(msg: WsMessage) {}
}