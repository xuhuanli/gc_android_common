package com.igancao.hpt_jsbridge

import android.content.Context

/**
 * 业务模块通过该接口暴露可注册的 Bridge handlers。
 * 每个业务模块都可以在自身 module 内提供实现，并通过 ServiceLoader 自动发现。
 */
interface BridgeModuleProvider {
    fun provideHandlers(context: Context): List<BridgeActionHandler>
}

