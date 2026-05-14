package com.igancao.hptasr.transport

internal sealed class WsMessage {
    class Text(val text: String) : WsMessage()
    class Binary(val bytes: ByteArray) : WsMessage()
}
