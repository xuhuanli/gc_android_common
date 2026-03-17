package com.igancao.hpt_jsbridge

/**
 * Copyright (c) 2026-03, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 * 定义外部模块需要实现的处理器接口
 */

interface BridgeActionHandler {
    /**
     * @param methodName JS 调用的方法名，例如 "sysShowToast", "doLogin"
     * @param traceId 本次调用的唯一标识，用于业务回传
     * @param requestStr JS 传过来的原始 request 字符串（保留给历史逻辑兼容）
     * @param payloadMap 解析完成的 payload，方便模块直接使用
     * @param bridge 可用于业务模块主动回传 BizResponse
     * @return BridgeHandleResult.NotHandled 表示不处理，交给下一个模块；
     *         BridgeHandleResult.Handled 表示已处理，syncReturn 用于同步返回场景。
     */
    fun handleAction(
        methodName: String,
        traceId: String,
        requestStr: String,
        payloadMap: Map<String, Any>,
        bridge: AndroidBaseBridge
    ): BridgeHandleResult
}
