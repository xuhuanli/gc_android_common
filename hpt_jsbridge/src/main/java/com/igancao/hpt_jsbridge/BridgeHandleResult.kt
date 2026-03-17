package com.igancao.hpt_jsbridge

sealed class BridgeHandleResult {
    data object NotHandled : BridgeHandleResult()

    /**
     * syncReturn 同步返回结果不需要包装成response格式，traceid等web侧自己都知道
     */
    data class Handled(val syncReturn: String? = null) : BridgeHandleResult()
}

