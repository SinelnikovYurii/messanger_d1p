package com.messenger.websocket.handler.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.websocket.model.MessageType;
import com.messenger.websocket.model.WebSocketMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PingMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(ChannelHandlerContext ctx, WebSocketMessage message) {
        WebSocketMessage pong = new WebSocketMessage();
        pong.setType(MessageType.PONG);
        try {
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(pong)));
        } catch (Exception e) {
            log.error("Error sending PONG: {}", e.getMessage());
        }
    }
}
