package com.igancao.hpt_jsbridge

/**
 * Copyright (c) 2026-02, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 * 需要客户端实现，把jsbridge运行时的日志上报到阿里云日志库
 *
 */

internal const val BRIDGE_LOG_TAG = "JSBridge-Log"

interface IBridgeLog {

    /**
     * 记录日志。
     */
    fun addLog(method: String, param: String? = null, throwable: Throwable? = null)
}