package com.igancao.hpt_jsbridge

import android.content.Context
import java.util.ServiceLoader

object BridgeModuleAutoRegistrar {
    /**
     * 自动发现并注册所有业务模块 handlers。
     * 业务模块只需提供 BridgeModuleProvider 的实现并配置 META-INF/services。
     */
    fun autoRegister(context: Context, bridge: AndroidBaseBridge): Int {
        var count = 0
        val loader = ServiceLoader.load(BridgeModuleProvider::class.java)
        loader.forEach { provider ->
            runCatching {
                provider.provideHandlers(context).forEach { handler ->
                    bridge.registerHandler(handler)
                    count++
                }
            }
        }
        return count
    }
}

