package com.igancao.hptasr.bean

import com.google.gson.annotations.SerializedName

/**
 * Copyright (c) 2026-03, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
open class BaseResponse<T>(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: T? = null
)