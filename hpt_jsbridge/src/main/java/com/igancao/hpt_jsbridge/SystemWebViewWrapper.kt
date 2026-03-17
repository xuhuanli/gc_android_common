package com.igancao.hpt_jsbridge

import android.webkit.WebView

/**
 * Copyright (c) 2026-02, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 *
 * 由于医生端用的是腾讯的X5web 不是系统webview的子类 所以这边用适配器模式把两种webview抽象出来
 *
 * // 示例，实际代码需要依赖 X5 SDK
 * import com.igancao.hpt_jsbridge.BridgeWebView
 * import com.tencent.smtt.sdk.WebView // 假设这是 X5 WebView 的类
 *
 * class X5WebViewWrapper(private val x5WebView: WebView) : BridgeWebView {
 *     override fun execJavascript(script: String) {
 *         // X5 WebView 也推荐在 UI 线程调用
 *         x5WebView.post {
 *             x5WebView.evaluateJavascript(script, null)
 *         }
 *     }
 * }
 *
 *
 * // --- 使用系统 WebView 的场景 ---
 * val mySystemWebView: android.webkit.WebView = findViewById(R.id.my_web_view)
 * val systemWebViewWrapper = SystemWebViewWrapper(mySystemWebView)
 * // 将包装器传入 Bridge，而不是 WebView 本身
 * val mySystemBridge = YourSpecificBridgeImplementation(systemWebViewWrapper)
 *
 *
 * // --- 使用腾讯 X5 WebView 的场景 ---
 * val myX5WebView: com.tencent.smtt.sdk.WebView = findViewById(R.id.my_x5_web_view)
 * val x5WebViewWrapper = X5WebViewWrapper(myX5WebView)
 * // 同样传入包装器
 * val myX5Bridge = YourSpecificBridgeImplementation(x5WebViewWrapper)
 *
 */

class SystemWebViewWrapper(internal val webView: WebView) : BridgeWebView {
    override fun execJavascript(script: String) {
        // 保证在 UI 线程执行
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}