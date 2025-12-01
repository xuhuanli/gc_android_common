package com.igancao.hptwebsocket

/**
 * Copyright (c) 2025-11, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */

sealed class WsMessage {
    class Text(val text: String) : WsMessage()
    class Binary(val bytes: ByteArray) : WsMessage()
}