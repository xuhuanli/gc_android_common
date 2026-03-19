# hpt_jsbridge

`hpt_jsbridge` 是一个 Android 侧 JSBridge 运行时模块，支持：
- Web -> Native 协议分发（多模块 handler）
- SysAck / BizResponse 双通道回调
- 通过 `ServiceLoader` 自动发现业务模块
- 配合 `hpt_jsbridge_processor`（KSP）生成协议常量，避免字符串硬编码

## 模块说明

- `hpt_jsbridge`：运行时库（Bridge 核心、协议解析、回调、模块注册）
- `hpt_jsbridge_processor`：注解处理器（扫描 `@BridgeAction`，生成 `BridgeActions` 常量类）

## 快速接入

### 1) Gradle 依赖

业务模块（或 app 模块）需要引入运行时和 KSP 处理器：
- maven地址配置
```groovy
maven(url = "http://nexus.igancao.com/repository/3rd_party/") {
            content {
                includeGroupByRegex("com\\.gancao.*") // 只检索下载group为com.gancao的包
            }
            isAllowInsecureProtocol = true
            credentials {
                // Be sure to add these non-sensitive credentials in order to retrieve dependencies from
                // the private repository.
                username="developer"
                password="72fb25481e"
            }
        }
```
- 如果需要hpt_jsbridge_processor进行常量生成，则需要添加ksp依赖和ksp插件
```kotlin
plugins {
    alias(libs.plugins.android.library) // 或 android.application
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp) // 可选
}

dependencies {
    // 本仓库多模块方式
    implementation(project(":hpt_jsbridge"))
    ksp(project(":hpt_jsbridge_processor")) // 可选

    // 如果走 maven（示例）
    // implementation("com.gancao:hpt_jsbridge:1.0.3")
    // ksp("com.gancao:hpt_jsbridge_processor:1.0.3")
}
```

可选：自定义生成类的包名和类名。

```kotlin
ksp {
    arg("bridgeActions.package", "com.igancao.bridge.generated")
    arg("bridgeActions.className", "BridgeActions")
}
```

默认生成位置与命名：
- 包名：`com.igancao.hpt_jsbridge.generated`
- 类名：`BridgeActions`

### 2) 定义协议名（用于生成常量）

在 Kotlin 代码中标记 `@BridgeAction`：

```kotlin
import com.igancao.hpt_jsbridge.BridgeAction

object JsBridgeActionSpec {
    @BridgeAction("sysShowToast")
    fun showToast() = Unit

    @BridgeAction
    fun getUserToken() = Unit // 默认取方法名：getUserToken
}
```

构建后会生成类似：

```kotlin
object BridgeActions {
    const val SYS_SHOW_TOAST = "sysShowToast"
    const val GET_USER_TOKEN = "getUserToken"
}
```

## 运行时使用

### 1) 包装 WebView

系统 WebView 可直接使用 `SystemWebViewWrapper`：

```kotlin
val webViewWrapper = SystemWebViewWrapper(systemWebView)
```

如果是 X5 WebView，按 `BridgeWebView` 接口自行实现包装器即可。

### 2) 实现 Bridge 入口类

继承 `AndroidBaseBridge`，在 `@JavascriptInterface` 方法中调用 `parseAndDispatchToModules(...)`：

```kotlin
import android.webkit.JavascriptInterface
import com.igancao.hpt_jsbridge.AndroidBaseBridge
import com.igancao.hpt_jsbridge.BridgeWebView
import com.xxx.bridge.generated.BridgeActions

class AppBridge(
    bridgeWebView: BridgeWebView
) : AndroidBaseBridge(bridgeWebView) {

    @JavascriptInterface
    fun sysShowToast(request: String?): String {
        return parseAndDispatchToModules(BridgeActions.SYS_SHOW_TOAST, request)
    }

    @JavascriptInterface
    fun getUserToken(request: String?): String {
        return parseAndDispatchToModules(BridgeActions.GET_USER_TOKEN, request)
    }
}
```

然后注入到 WebView（名字可配置，默认 `webView`）：

```kotlin
JsBridgeConfig.bridgeName = "webView"
webView.addJavascriptInterface(appBridge, JsBridgeConfig.bridgeName)
```

### 3) 实现业务 Handler

业务模块实现 `BridgeActionHandler`，按 `methodName` 分发：

```kotlin
import com.igancao.hpt_jsbridge.AndroidBaseBridge
import com.igancao.hpt_jsbridge.BridgeActionHandler
import com.igancao.hpt_jsbridge.BridgeHandleResult

class CommonBridgeHandler : BridgeActionHandler {
    override fun handleAction(
        methodName: String,
        traceId: String,
        requestStr: String,
        payloadMap: Map<String, Any>,
        bridge: AndroidBaseBridge
    ): BridgeHandleResult {
        return when (methodName) {
            "sysShowToast" -> {
                // 同步返回给 JS（可选）
                BridgeHandleResult.Handled("""{"ok":true}""")
            }
            "getUserToken" -> {
                // 异步业务结束后，手动回传 BizResponse
                bridge.invokeOnBridgeBizResponse(
                    traceId = traceId,
                    code = 0,
                    message = "success",
                    data = mapOf("token" to "xxx")
                )
                BridgeHandleResult.Handled()
            }
            else -> BridgeHandleResult.NotHandled
        }
    }
}
```

### 4) 暴露 Provider 并自动注册

实现 `BridgeModuleProvider`：

```kotlin
import android.content.Context
import com.igancao.hpt_jsbridge.BridgeActionHandler
import com.igancao.hpt_jsbridge.BridgeModuleProvider

class CommonBridgeProvider : BridgeModuleProvider {
    override fun provideHandlers(context: Context): List<BridgeActionHandler> {
        return listOf(CommonBridgeHandler())
    }
}
```

在对应模块创建 SPI 文件：

`src/main/resources/META-INF/services/com.igancao.hpt_jsbridge.BridgeModuleProvider`

内容为实现类全限定名（一行一个）：

```text
com.xxx.bridge.CommonBridgeProvider
```

初始化时自动注册：

```kotlin
val count = BridgeModuleAutoRegistrar.autoRegister(context, appBridge)
```

也支持手动注册：

```kotlin
appBridge.registerHandler(CommonBridgeHandler())
```

## 协议格式约定

请求体 `BridgeRequest`：

```json
{
  "traceid": "xxxx",
  "timestamp": 1730000000000,
  "payload": {},
  "meta": {
    "version": "1.0.0"
  }
}
```

回调体 `BridgeResponse`：

```json
{
  "traceid": "xxxx",
  "timestamp": 1730000001000,
  "code": 0,
  "message": "success",
  "data": {}
}
```

Native -> Web 回调函数：
- `window.onBridgeSysAck(response)`：SDK 在请求解析后自动回调
- `window.onBridgeBizResponse(response)`：业务完成后按需手动回调

## 错误码约定（当前实现）

- `0`：成功
- `-1`：未找到可处理模块
- `-2`：请求参数或 JSON 格式错误
- `-99`：预留（授权校验失败）

## 生命周期与日志

- 页面销毁时调用 `appBridge.destroy()` 释放协程作用域
- 可通过 `JsBridgeConfig.enableLog` 控制日志
- 可实现 `IBridgeLog` 对接业务日志系统

## 混淆说明

`hpt_jsbridge` 已在 `consumer-rules.pro` 中保留 `BridgeModuleProvider` 实现类构造器，确保 `ServiceLoader` 可发现：

```pro
-keep class * implements com.igancao.hpt_jsbridge.BridgeModuleProvider {
    public <init>();
}
```

