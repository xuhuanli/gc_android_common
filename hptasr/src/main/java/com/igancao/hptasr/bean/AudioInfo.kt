package com.igancao.hptasr.bean

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Copyright (c) 2025-12, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */

@Keep
data class AudioInfo(
    val format: String, // 音频容器格式
    val rate: Int, // 音频采样率
    val channel: Int, // 音频声道数
    val bits: Int, // 音频采样点位数
    val duration: Int, //时间
    val chunkSize: Int // 音频分片大小
)

@Keep
data class ConfigInfo(
    @SerializedName("third_party") var thirdParty: String? = "",
    @SerializedName("audio_meta") val audioMeta: AudioMeta? = null,
)
@Keep
data class AudioMeta(
    @SerializedName("format") val format: String? = "",
    @SerializedName("duration") val duration: Int? = 200,
    @SerializedName("codec") val codec: String? = "",
    @SerializedName("rate") val rate: Int? = 16000,
    @SerializedName("bits") val bits: Int? = 16,
    @SerializedName("channel") val channel: Int? = 1,
    @SerializedName("chunk_size") val chunkSize: Int? = 3200 // 音频分片大小
)
