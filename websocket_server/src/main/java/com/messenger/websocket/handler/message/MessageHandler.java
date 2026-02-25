package com.messenger.websocket.handler.message;

import com.messenger.websocket.model.WebSocketMessage;
import io.netty.channel.ChannelHandlerContext;

/**
 * Стратегия обработки WebSocket-сообщения.
 * Каждый тип сообщения получает свою реализацию.
 */
public interface MessageHandler {
    void handle(ChannelHandlerContext ctx, WebSocketMessage message);
}
