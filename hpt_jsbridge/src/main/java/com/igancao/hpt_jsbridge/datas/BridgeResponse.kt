package com.igancao.hpt_jsbridge.datas

import com.google.gson.annotations.SerializedName

/**
 * Copyright (c) 2026-02, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
data class BridgeResponse(
    @SerializedName("traceid")
    val traceId: String, // 请求对应id
    @SerializedName("timestamp")
    val timestamp: Long, // 响应时间戳
    @SerializedName("code")
    val code: Int, // 响应码 0-成功 负数-失败（参照java错误码）
    @SerializedName("message")
    val message: String, // 响应消息
    @SerializedName("data")
    val data: Any? = null // 响应数据
)