# HPT WebSocket 模块说明（更新版）

## 模块简介
`WebSocketManager` 

> 注意：手动心跳逻辑已删除，完全依赖 OkHttp 的 pingInterval。

---

## 参数说明

### init(config: WsConfig, listener: WsListener)
- **config: WsConfig**  
  WebSocket 配置对象，包括以下字段：
    - `url: String` —— WebSocket 服务地址
    - `reconnectBaseDelay: Long` —— 初始重连延迟（毫秒）
    - `reconnectMaxDelay: Long` —— 最大重连延迟（毫秒）
    - 其他可选参数，可根据业务扩展

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
    - `onSendQueueOverFlow(msg: WsMessage)` —— 发送队列溢出回调
    - `onReceiveQueueOverFlow(bytes: ByteArray)` —— 接收队列溢出回调

---

## 消息发送接口

### send(msg: WsMessage)
- **msg: WsMessage**  
  要发送的消息对象：
    - `WsMessage.Text` —— 文本消息（命令或心跳 JSON）
    - `WsMessage.Binary` —— 二进制消息（PCM 音频或其他数据）

**说明**：
- **所有消息先入队列**，保证发送顺序
- 队列满时触发 `onSendQueueOverFlow` 回调
- 已连接时从队列中按顺序取出发送
- 文本和二进制消息发送前触发 `onSendText/onSendBinary` 回调

---

## WebSocket 连接管理

### connect()
- 发起 WebSocket 连接
- 调用 `wsListener.buildHeaders()` 为不同平台添加自定义 Header
- 连接成功后 flush 队列中所有消息，按顺序发送

### disconnect()
- 手动关闭连接
- 不触发自动重连

### stopManual()
- 完全停止 WebSocketManager
- 清空队列、取消后台任务
- 调用 disconnect() 关闭连接

---

## 队列管理

- **发送队列（messageQueue）**：LinkedBlockingQueue，容量 200
- **接收队列（responseQueue）**：LinkedBlockingQueue，容量 200
- 消息全部先入队，再按顺序发送 / 处理
- 队列满时触发对应的溢出回调，避免阻塞或丢失顺序

---

## WebSocket 回调方法

| 方法 | 说明 |
|------|------|
| `onOpen(webSocket, response)` | 连接成功回调，触发 flush 队列 |
| `onMessage(webSocket, text)` | 收到文本消息，加入接收队列触发 `onTextMessage` |
| `onMessage(webSocket, bytes)` | 收到二进制消息，加入接收队列触发 `onBinaryMessage` |
| `onClosing(webSocket, code, reason)` | 对端请求关闭连接回调，触发 `onClosing` |
| `onClosed(webSocket, code, reason)` | 连接完全关闭回调，非手动关闭时触发重连，触发 `onClosed` |
| `onFailure(webSocket, t, response)` | 连接异常回调，触发 `onFailure` 并启动重连 |

---

## 重连机制

- 使用指数退避算法：`delay = reconnectBaseDelay * 2^(attempt - 1)`
- 最大延迟不超过 `reconnectMaxDelay`
- 自动重连触发场景：
    - 网络异常 (`onFailure`)
    - 服务器非手动关闭 (`onClosed`，code != 1000)