package com.igancao.hptasr.bean

/**
 * Copyright (c) 2026-03, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
import com.google.gson.annotations.SerializedName

/**
 * ASR 识别结果数据模型
 */
data class AsrResult(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("event") val event: Int, // 事件类型
    @SerializedName("payload_sequence") val payloadSequence: Int, // 包序列号
    @SerializedName("payload_size") val payloadSize: Int, // 有效载荷大小
    @SerializedName("payload_msg") val payloadMsg: PayloadMsg?
)

data class PayloadMsg(
    @SerializedName("audio_info") val audioInfo: AudioInfoResponse?,
    @SerializedName("result") val result: ResultData?
)

data class AudioInfoResponse(
    @SerializedName("duration") val duration: Int // 音频时长
)

data class ResultData(
    @SerializedName("text") val text: String, // 当前完整的识别文本
    @SerializedName("utterances") val utterances: List<Utterance>? // 具体的语句分段信息
)

data class Utterance(
    @SerializedName("definite") val definite: Boolean, // 是否已确定（不再更改）
    @SerializedName("end_time") val endTime: Int, // 结束时间
    @SerializedName("start_time") val startTime: Int, // 开始时间
    @SerializedName("text") val text: String, // 该段文本
)