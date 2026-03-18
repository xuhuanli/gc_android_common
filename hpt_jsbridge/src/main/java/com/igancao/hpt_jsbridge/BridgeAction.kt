package com.igancao.hpt_jsbridge

/**
 * 标记 JS Bridge 协议方法，KSP 会基于此注解生成协议常量。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class BridgeAction(
    /**
     * 可选：自定义协议名，默认取方法名。
     */
    val value: String = ""
)

