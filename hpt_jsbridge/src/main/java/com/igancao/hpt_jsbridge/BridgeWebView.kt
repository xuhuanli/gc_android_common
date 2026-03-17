package com.igancao.hpt_jsbridge

/**
 * Copyright (c) 2026-02, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */

/**
 * 依赖抽象的 BridgeWebView 接口，而不是具体的 WebView 实现
 *
 * Bridge 功能所需的 WebView 抽象接口。
 * 任何想要集成 Bridge 的 WebView（无论是系统 WebView 还是 X5 WebView）都应实现此接口。
 */
interface BridgeWebView {
    /**
     * 在 UI 线程安全地执行一段 JavaScript 脚本。
     * @param script 要执行的 JS 脚本字符串。
     */
    fun execJavascript(script: String)
}