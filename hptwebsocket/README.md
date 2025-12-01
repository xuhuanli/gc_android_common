# HPT WebSocket 模块说明

## 模块简介
`WebSocketManager`  WebSocket 客户端管理类。

---

## 参数说明

### init(config: WsConfig, listener: WsListener)
- **config: WsConfig**  
  WebSocket 配置对象，包括以下字段：
    - `url: String` —— WebSocket 服务地址
    - `pingInterval: Long` —— 心跳发送间隔（毫秒）
    - `pingTimeout: Long` —— 心跳超时时间（毫秒）
    - `reconnectBaseDelay: Long` —— 初始重连延迟（毫秒）
    - `reconnectMaxDelay: Long` —— 最大重连延迟（毫秒）
    - 其他可选参数，根据具体实现可扩展

- **listener: WsListener**  
  平台回调接口，用于处理消息和连接状态，包括：
    - `buildHeaders(builder: Request.Builder, config: WsConfig)` —— 构建平台特定的 WebSocket Header
    - `onOpen()` —— 连接成功回调
    - `onTextMessage(text: String)` —— 收到文本消息回调
    - `onBinaryMessage(bytes: ByteArray)` —— 收到二进制消息回调
    - `onClosing(code: Int, reason: String)` —— 对端请求关闭连接回调
    - `onClosed(code: Int, reason: String)` —— 连接关闭回调
    - `onFailure(t: Throwable)` —— 连接异常回调
    - `onSendText(ws: WebSocket, text: String)` —— 文本消息发送回调
    - `onSendBinary(ws: WebSocket, bytes: ByteArray)` —— 二进制消息发送回调

---

## 消息发送接口

### send(msg: WsMessage)
- **msg: WsMessage**  
  要发送的消息对象，可以是：
    - `WsMessage.Text` —— 文本消息，例如心跳或命令：`{"cmd":"start"}`、`{"cmd":"end"}`
    - `WsMessage.Binary` —— 二进制消息，例如 PCM 音频数据

**说明**：
- 如果 WebSocket 未连接，消息会自动入队列，连接成功后自动发送
- 文本消息和二进制消息在发送前会触发 `wsListener.onSendText/onSendBinary` 回调

---

## WebSocket 连接管理

### connect()
- 发起 WebSocket 连接
- 调用 `wsListener.buildHeaders()` 为不同平台添加自定义 Header
- 连接成功后会自动发送队列中未发送的消息，并启动心跳

### disconnect()
- 手动关闭连接
- 不会触发自动重连

### stopManual()
- 彻底停止 WebSocket
- 停止心跳、心跳超时任务
- 取消后台协程任务
- 调用 `disconnect()` 关闭连接

---

## WebSocket 回调方法

| 方法 | 说明 |
|------|------|
| `onOpen(webSocket, response)` | 连接成功回调，触发发送队列及心跳 |
| `onMessage(webSocket, text)` | 收到文本消息，触发 `wsListener.onTextMessage` |
| `onMessage(webSocket, bytes)` | 收到二进制消息，入 `responseQueue` 并触发 `wsListener.onBinaryMessage` |
| `onClosing(webSocket, code, reason)` | 对端请求关闭连接回调，触发 `wsListener.onClosing` |
| `onClosed(webSocket, code, reason)` | 连接完全关闭回调，非手动关闭时触发重连，触发 `wsListener.onClosed` |
| `onFailure(webSocket, t, response)` | 连接异常回调，触发 `wsListener.onFailure` 并启动重连 |

---

## 心跳机制

- 定期发送心跳包保持长连接
- 心跳发送间隔：`pingInterval`
- 心跳超时时间：`pingTimeout`
- 收到任何消息都会重置心跳超时计时器
- 心跳超时会自动断开连接并触发重连

---

## 重连机制

- 使用指数退避算法：`delay = reconnectBaseDelay * 2^(attempt - 1)`
- 最大延迟不超过 `reconnectMaxDelay`
- 自动重连触发场景：
    - 网络异常 (`onFailure`)
    - 服务器非手动关闭 (`onClosed`，code != 1000)

---

## 消息队列与 Flow

- **messageQueue**：存储连接前发送的消息，连接成功后自动发送
- **responseQueue**：存储收到的二进制消息，由后台协程 parseLoop 解析
- **_messagesFlow**：最终解析后的消息通过 SharedFlow 下发给 UI 层

---