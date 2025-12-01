package com.igancao.hptwebsocket

/**
 * Copyright (c) 2025-11, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 * 可以扩展，比如 token、header、自定义参数等。
 */

data class WsConfig(
    val url: String,
    val pingInterval: Long = 10_000, // 每隔10s发送一次心跳包
    val pingTimeout: Long = 30_000, // 心跳包超时时间，必须大于pingInterval，当前设置30s，中间可能会有未发送消息进入队列。
    val reconnectBaseDelay: Long = 2000,  // 初次重连延迟
    val reconnectMaxDelay: Long = 60_000, // 最大重连延迟
)