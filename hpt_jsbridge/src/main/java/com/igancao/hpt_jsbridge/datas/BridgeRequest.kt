package com.igancao.hpt_jsbridge.datas

import com.google.gson.annotations.SerializedName

/**
 * Copyright (c) 2026-02, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
data class BridgeRequest(
    @SerializedName("traceid")
    val traceId: String,  // 唯一请求id
    @SerializedName("timestamp")
    val timestamp: Long, // 请求时间戳 精确到毫秒
    @SerializedName("payload")
    val payload: Any?, // 请求荷载 要求payload也得是一个json对象
    @SerializedName("meta")
    val meta: Meta // 元数据
)

/**
 * 元数据定义
 */
data class Meta(
    @SerializedName("version")
    val version: String // 请求客户端版本号
)