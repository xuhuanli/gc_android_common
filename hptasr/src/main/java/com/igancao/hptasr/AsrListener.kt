package com.igancao.hptasr

/**
 * asr回调接口 不保证回调在主线程
 */
interface AsrListener {
    fun onReady()
    /**
     * 识别结果回调（包含中间结果和最终结果）
     * UI层直接将此字符串设置给 TextView 即可实现流式展示
     */
    fun onResult(result: String)
    fun onError(error: String)
}
