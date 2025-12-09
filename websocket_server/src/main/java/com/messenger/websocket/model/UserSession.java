package com.messenger.websocket.model;

import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {
    private String sessionId;
    private ChannelHandlerContext context;
    private String username;
    private Long userId;
    private Long currentChatId;

    // Конструктор для совместимости с SessionManager
    public UserSession(String sessionId, ChannelHandlerContext context, String username, Long userId) {
        this.sessionId = sessionId;
        this.context = context;
        this.username = username;
        this.userId = userId;
        this.currentChatId = null;
    }
}
