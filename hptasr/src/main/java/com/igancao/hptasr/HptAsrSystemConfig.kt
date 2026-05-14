package com.igancao.hptasr

/**
 * 全局网络配置单例，对齐 iOS HPTASRSystemConfig。
 */
object HptAsrSystemConfig {
    var baseUrl: String = ""
    var identifier: String = ""
    var thirdParty: String = ""
    var ticket: String = ""
    var typeItemCode: String = ""

    /**
     * 校验配置有效性：
     * - baseUrl 和 thirdParty 必填；
     * - identifier 不为空时，ticket 和 typeItemCode 可为空；
     * - identifier 为空时，ticket 和 typeItemCode 都必须不为空。
     */
    fun isValid(): Boolean {
        if (baseUrl.isBlank() || thirdParty.isBlank()) return false
        return identifier.isNotBlank() || (ticket.isNotBlank() && typeItemCode.isNotBlank())
    }
}