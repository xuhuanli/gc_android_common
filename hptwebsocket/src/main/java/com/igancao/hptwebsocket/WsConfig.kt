package com.igancao.hptwebsocket

/**
 * Copyright (c) 2025-11, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 * 可以扩展，比如 token、header、自定义参数等。
 */

data class WsConfig(
    val url: String,
    val reconnectBaseDelay: Long = 1000,  // 初次重连延迟
    val reconnectMaxDelay: Long = 60_000, // 最大重连延迟
    val sendInterval: Long = 200, // 每段消息的发送间隔，默认200ms
)