package com.igancao.hptasr.bean

import androidx.annotation.Keep
import org.json.JSONObject

@Keep
data class AudioInfo(
    val format: String,
    val rate: Int,
    val channel: Int,
    val bits: Int,
    val duration: Int,
    val chunkSize: Int,
) {
    companion object {
        private const val DEFAULT_FORMAT = "pcm"
        private const val DEFAULT_DURATION = 200
        private const val DEFAULT_CHUNK_SIZE = 3200

        /**
         * 从配置 JSON（对应 iOS dataDic）中提取 audio_meta 构造 AudioInfo。
         *
         * @throws IllegalArgumentException audio_meta 缺失或 rate/channel/bits 无效
         */
        fun fromConfigJson(configJson: JSONObject): AudioInfo {
            val meta = configJson.optJSONObject("audio_meta")
                ?: throw IllegalArgumentException("audio_meta is required")

            val rate = requirePositive(meta.optInt("rate", 0), "audio_meta.rate")
            val channel = requirePositive(meta.optInt("channel", 0), "audio_meta.channel")
            val bits = requirePositive(meta.optInt("bits", 0), "audio_meta.bits")

            return AudioInfo(
                format = meta.optString("format").takeIf { it.isNotBlank() } ?: DEFAULT_FORMAT,
                rate = rate,
                channel = channel,
                bits = bits,
                duration = meta.optInt("duration", 0).takeIf { it > 0 } ?: DEFAULT_DURATION,
                chunkSize = meta.optInt("chunk_size", 0).takeIf { it > 0 } ?: DEFAULT_CHUNK_SIZE,
            )
        }

        private fun requirePositive(value: Int, name: String): Int {
            require(value > 0) { "$name must be greater than 0" }
            return value
        }
    }
}
