package com.messenger.websocket.handler.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.websocket.model.MessageType;
import com.messenger.websocket.model.WebSocketMessage;
import com.messenger.websocket.service.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ChatMessageHandler implements MessageHandler {

    private final SessionManager sessionManager;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(ChannelHandlerContext ctx, WebSocketMessage message) {
        String sessionId = ctx.channel().id().asShortText();
        if (!sessionManager.isAuthenticated(sessionId)) {
            sendError(ctx, "Not authenticated");
            return;
        }

        String username = sessionManager.getUsername(sessionId);
        Long userId = sessionManager.getUserId(sessionId);

        log.info("[CHAT] Message from {} (ID: {}), chatId: {}", username, userId, message.getChatId());

        try {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("content", message.getContent());
            messageData.put("senderId", userId);
            messageData.put("senderUsername", username);
            messageData.put("chatId", message.getChatId());
            messageData.put("messageType", "TEXT");
            messageData.put("timestamp", LocalDateTime.now().toString());

            String jsonMessage = objectMapper.writeValueAsString(messageData);
            String chatKey = String.valueOf(message.getChatId());

            kafkaTemplate.send("chat-messages", chatKey, jsonMessage)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("[KAFKA] Message sent, offset: {}", result.getRecordMetadata().offset());
                    } else {
                        log.error("[KAFKA] Failed to send message: {}", ex.getMessage(), ex);
                    }
                });

            WebSocketMessage confirmation = new WebSocketMessage();
            confirmation.setType(MessageType.MESSAGE_SENT);
            confirmation.setContent("Message sent successfully");
            send(ctx, confirmation);

        } catch (Exception e) {
            log.error("[CHAT] Error processing message: {}", e.getMessage(), e);
            sendError(ctx, "Failed to process message");
        }
    }

    private void send(ChannelHandlerContext ctx, WebSocketMessage msg) {
        try {
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    private void sendError(ChannelHandlerContext ctx, String error) {
        WebSocketMessage err = new WebSocketMessage();
        err.setType(MessageType.ERROR);
        err.setContent(error);
        send(ctx, err);
    }
}
