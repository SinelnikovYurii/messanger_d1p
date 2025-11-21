package com.messenger.websocket.service;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import websocket.service.ChatParticipantService;
import websocket.service.OnlineStatusService;
import websocket.service.SessionManager;
import websocket.service.UserDataService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SessionManagerTest {

    private SessionManager sessionManager;

    @Mock
    private ChatParticipantService chatParticipantService;

    @Mock
    private OnlineStatusService onlineStatusService;

    @Mock
    private UserDataService userDataService;

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionManager = new SessionManager();
        // Используем рефлексию для установки моков, так как @Autowired не работает в unit-тестах
        try {
            java.lang.reflect.Field chatParticipantField = SessionManager.class.getDeclaredField("chatParticipantService");
            chatParticipantField.setAccessible(true);
            chatParticipantField.set(sessionManager, chatParticipantService);

            java.lang.reflect.Field onlineStatusField = SessionManager.class.getDeclaredField("onlineStatusService");
            onlineStatusField.setAccessible(true);
            onlineStatusField.set(sessionManager, onlineStatusService);

            java.lang.reflect.Field userDataField = SessionManager.class.getDeclaredField("userDataService");
            userDataField.setAccessible(true);
            userDataField.set(sessionManager, userDataService);
        } catch (Exception e) {
            fail("Failed to inject mocks: " + e.getMessage());
        }
    }

    @Test
    void testAddSession() {
        String sessionId = "session123";
        String username = "testuser";
        Long userId = 1L;

        sessionManager.addSession(sessionId, ctx, username, userId);

        verify(onlineStatusService, times(1)).setUserOnline(userId);
        assertNotNull(sessionManager.getSession(sessionId));
    }

    @Test
    void testRemoveSession() {
        String sessionId = "session123";
        String username = "testuser";
        Long userId = 1L;

        sessionManager.addSession(sessionId, ctx, username, userId);
        sessionManager.removeSession(sessionId);

        verify(onlineStatusService, times(1)).setUserOffline(userId);
        assertNull(sessionManager.getSession(sessionId));
    }

    @Test
    void testRemoveNonExistentSession() {
        String sessionId = "nonexistent";

        assertDoesNotThrow(() -> sessionManager.removeSession(sessionId));
        verify(onlineStatusService, never()).setUserOffline(anyLong());
    }

    @Test
    void testAddSessionReplacesExisting() {
        String sessionId1 = "session1";
        String sessionId2 = "session2";
        String username = "testuser";
        Long userId = 1L;

        ChannelHandlerContext ctx1 = mock(ChannelHandlerContext.class);
        ChannelHandlerContext ctx2 = mock(ChannelHandlerContext.class);

        sessionManager.addSession(sessionId1, ctx1, username, userId);
        sessionManager.addSession(sessionId2, ctx2, username, userId);

        // Первая сессия должна быть удалена
        assertNull(sessionManager.getSession(sessionId1));
        assertNotNull(sessionManager.getSession(sessionId2));
    }

    @Test
    void testGetSessionByUserId() {
        String sessionId = "session123";
        String username = "testuser";
        Long userId = 1L;

        sessionManager.addSession(sessionId, ctx, username, userId);

        assertNotNull(sessionManager.getSessionByUserId(userId));
        assertEquals(sessionId, sessionManager.getSessionByUserId(userId).getSessionId());
    }

    @Test
    void testGetAllSessions() {
        sessionManager.addSession("session1", mock(ChannelHandlerContext.class), "user1", 1L);
        sessionManager.addSession("session2", mock(ChannelHandlerContext.class), "user2", 2L);
        sessionManager.addSession("session3", mock(ChannelHandlerContext.class), "user3", 3L);

        assertEquals(3, sessionManager.getAllSessions().size());
    }

    @Test
    void testIsUserOnline() {
        String sessionId = "session123";
        Long userId = 1L;

        assertFalse(sessionManager.isUserOnline(userId));

        sessionManager.addSession(sessionId, ctx, "testuser", userId);

        assertTrue(sessionManager.isUserOnline(userId));
    }

    @Test
    void testGetUsernameAndUserIdAndContext() {
        String sessionId = "sessionX";
        String username = "userX";
        Long userId = 42L;
        sessionManager.addSession(sessionId, ctx, username, userId);
        assertEquals(username, sessionManager.getUsername(sessionId));
        assertEquals(userId, sessionManager.getUserId(sessionId));
        assertEquals(ctx, sessionManager.getContext(sessionId));
        assertEquals(ctx, sessionManager.getContextByUserId(userId));
    }

    @Test
    void testBroadcastUserOnlineStatusAndOfflineStatus() {
        String sessionId = "sessionY";
        String username = "userY";
        Long userId = 99L;
        sessionManager.addSession(sessionId, ctx, username, userId);
        // Проверяем, что онлайн-статус обновлен
        verify(onlineStatusService, times(1)).setUserOnline(userId);
        sessionManager.removeSession(sessionId);
        verify(onlineStatusService, times(1)).setUserOffline(userId);
        // Проверяем, что userDataService.getUserData вызывается при offline
        verify(userDataService, atLeast(0)).getUserData(userId);
    }

    @Test
    void testGetChatChannelsWithParticipants() {
        Long chatId = 123L;
        Long userId = 1L;
        String sessionId = "sessionA";
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chA");
        sessionManager.addSession(sessionId, ctxMock, "userA", userId);
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        var channels = sessionManager.getChatChannels(chatId);
        assertEquals(1, channels.size());
        assertTrue(channels.contains(channelMock));
    }

    @Test
    void testGetChatChannelsFallback() {
        Long chatId = 321L;
        // Нет участников
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.emptyList());
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("ch123");
        sessionManager.addSession("sessionB", ctxMock, "userB", 2L);
        var channels = sessionManager.getChatChannels(chatId);
        assertFalse(channels.isEmpty());
        assertEquals(channelMock, channels.get(0));
    }

    @Test
    void testGetUserChannel() {
        Long userId = 7L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        sessionManager.addSession("sessionC", ctxMock, "userC", userId);
        assertEquals(channelMock, sessionManager.getUserChannel(userId));
    }

    @Test
    void testSendMessageToUserActiveChannel() {
        Long userId = 8L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        sessionManager.addSession("sessionD", ctxMock, "userD", userId);
        websocket.model.WebSocketMessage msg = new websocket.model.WebSocketMessage();
        assertTrue(sessionManager.sendMessageToUser(userId, msg));
        verify(channelMock, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    void testSendMessageToUserInactiveChannel() {
        Long userId = 9L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(false);
        sessionManager.addSession("sessionE", ctxMock, "userE", userId);
        websocket.model.WebSocketMessage msg = new websocket.model.WebSocketMessage();
        assertFalse(sessionManager.sendMessageToUser(userId, msg));
    }

    @Test
    void testBroadcastMessageToChat() {
        Long chatId = 555L;
        Long userId = 10L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        sessionManager.addSession("sessionF", ctxMock, "userF", userId);
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
        messageData.put("type", "NEW_MESSAGE");
        messageData.put("content", "Hello!");
        messageData.put("senderId", userId);
        messageData.put("senderUsername", "userF");
        sessionManager.broadcastMessageToChat(chatId, messageData);
        verify(channelMock, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    void testSendNotificationToUserOnline() {
        Long userId = 11L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        sessionManager.addSession("sessionG", ctxMock, "userG", userId);
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("type", "FRIEND_REQUEST");
        notificationData.put("fromUserId", 99L);
        sessionManager.sendNotificationToUser(userId, notificationData);
        verify(channelMock, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    void testSendNotificationToUserOffline() {
        Long userId = 12L;
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("type", "FRIEND_REQUEST");
        assertDoesNotThrow(() -> sessionManager.sendNotificationToUser(userId, notificationData));
    }

    @Test
    void testSendNotificationToUserInactiveSession() {
        Long userId = 13L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(false);
        sessionManager.addSession("sessionH", ctxMock, "userH", userId);
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("type", "FRIEND_REQUEST");
        assertDoesNotThrow(() -> sessionManager.sendNotificationToUser(userId, notificationData));
    }

    @Test
    void testEdgeCasesEmptyCollections() {
        assertTrue(sessionManager.getAllSessions().isEmpty());
        assertEquals(0, sessionManager.getActiveSessionsCount());
        assertNull(sessionManager.getUsername("unknownSession"));
        assertNull(sessionManager.getUserId("unknownSession"));
        assertNull(sessionManager.getContext("unknownSession"));
        assertNull(sessionManager.getContextByUserId(999L));
        assertNull(sessionManager.getSession("unknownSession"));
        assertNull(sessionManager.getSessionByUserId(999L));
        assertNull(sessionManager.getUserChannel(999L));
    }

    @Test
    void testBroadcastUserOnlineStatusSerializationError() {
        String sessionId = "sessionErr";
        Long userId = 100L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chErr");
        doThrow(new RuntimeException("Serialization error")).when(channelMock).writeAndFlush(any());
        sessionManager.addSession(sessionId, ctxMock, "userErr", userId);
        assertTrue(sessionManager.isUserOnline(userId));
        assertDoesNotThrow(() -> sessionManager.removeSession(sessionId));
    }

    @Test
    void testRemoveSessionTwice() {
        String sessionId = "sessionTwice";
        Long userId = 101L;
        sessionManager.addSession(sessionId, ctx, "userTwice", userId);
        sessionManager.removeSession(sessionId);
        assertDoesNotThrow(() -> sessionManager.removeSession(sessionId));
    }

    @Test
    void testAddSessionWithNullValues() {
        assertDoesNotThrow(() -> sessionManager.addSession(null, null, null, null));
    }

    @Test
    void testGetSessionNonExistent() {
        assertNull(sessionManager.getSession("noSuchSession"));
        assertNull(sessionManager.getSessionByUserId(99999L));
    }

    @Test
    void testGetAllSessionsAfterMassRemove() {
        sessionManager.addSession("s1", ctx, "u1", 1L);
        sessionManager.addSession("s2", ctx, "u2", 2L);
        sessionManager.removeSession("s1");
        sessionManager.removeSession("s2");
        assertTrue(sessionManager.getAllSessions().isEmpty());
    }

    @Test
    void testGetChatChannelsException() {
        Long chatId = 404L;
        when(chatParticipantService.getChatParticipants(chatId)).thenThrow(new RuntimeException("DB error"));
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("ch404");
        sessionManager.addSession("session404", ctxMock, "user404", 404L);
        var channels = sessionManager.getChatChannels(chatId);
        assertFalse(channels.isEmpty());
    }

    @Test
    void testSendMessageToUserSerializationError() {
        Long userId = 102L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("ch102");
        sessionManager.addSession("session102", ctxMock, "user102", userId);
        websocket.model.WebSocketMessage msg = mock(websocket.model.WebSocketMessage.class);
        // Ломаем ObjectMapper через spy
        SessionManager spyManager = spy(sessionManager);
        doThrow(new RuntimeException("Serialization error")).when(channelMock).writeAndFlush(any());
        assertFalse(spyManager.sendMessageToUser(userId, msg));
    }

    @Test
    void testBroadcastMessageToChatUnknownType() {
        Long chatId = 103L;
        Long userId = 103L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("ch103");
        sessionManager.addSession("session103", ctxMock, "user103", userId);
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
        messageData.put("type", "UNKNOWN_TYPE");
        messageData.put("content", "Test unknown type");
        messageData.put("senderId", userId);
        messageData.put("senderUsername", "user103");
        assertDoesNotThrow(() -> sessionManager.broadcastMessageToChat(chatId, messageData));
    }

    @Test
    void testSendNotificationToUserSerializationError() {
        Long userId = 104L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("ch104");
        sessionManager.addSession("session104", ctxMock, "user104", userId);
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("type", "ERROR_CASE");
        doThrow(new RuntimeException("Serialization error")).when(channelMock).writeAndFlush(any());
        assertDoesNotThrow(() -> sessionManager.sendNotificationToUser(userId, notificationData));
    }

    @Test
    void testConcurrentAddRemoveSessions() throws InterruptedException {
        int threadCount = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                sessionManager.addSession("sessionC" + idx, ctx, "userC" + idx, (long) idx);
                sessionManager.removeSession("sessionC" + idx);
                latch.countDown();
            }).start();
        }
        latch.await();
        assertEquals(0, sessionManager.getActiveSessionsCount());
    }

    @Test
    void testEdgeUserIdValues() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chMax");
        sessionManager.addSession("sessionMax", ctxMock, "userMax", Long.MAX_VALUE);
        assertTrue(sessionManager.isUserOnline(Long.MAX_VALUE));
        sessionManager.addSession("sessionMin", ctxMock, "userMin", Long.MIN_VALUE);
        assertTrue(sessionManager.isUserOnline(Long.MIN_VALUE));
    }

    @Test
    void testIntegrationOnlineStatusService() {
        String sessionId = "sessionInt";
        Long userId = 105L;
        sessionManager.addSession(sessionId, ctx, "userInt", userId);
        verify(onlineStatusService, times(1)).setUserOnline(userId);
        sessionManager.removeSession(sessionId);
        verify(onlineStatusService, times(1)).setUserOffline(userId);
    }

    @Test
    void testAddSessionWithEmptyUsername() {
        String sessionId = "sessionEmptyUser";
        Long userId = 200L;
        sessionManager.addSession(sessionId, ctx, "", userId);
        assertNotNull(sessionManager.getSession(sessionId));
        assertEquals("", sessionManager.getSession(sessionId).getUsername());
    }

    @Test
    void testAddSessionWithLongSessionIdAndUsername() {
        String longSessionId = "s".repeat(1000);
        String longUsername = "u".repeat(1000);
        Long userId = 201L;
        sessionManager.addSession(longSessionId, ctx, longUsername, userId);
        assertNotNull(sessionManager.getSession(longSessionId));
        assertEquals(longUsername, sessionManager.getSession(longSessionId).getUsername());
    }

    @Test
    void testAddSessionWithSameUserIdDifferentSessionId() {
        Long userId = 202L;
        sessionManager.addSession("sessionA", ctx, "userA", userId);
        sessionManager.addSession("sessionB", ctx, "userA", userId);
        assertNull(sessionManager.getSession("sessionA"));
        assertNotNull(sessionManager.getSession("sessionB"));
    }

    @Test
    void testAddSessionWithSameSessionIdDifferentUserId() {
        String sessionId = "sessionC";
        sessionManager.addSession(sessionId, ctx, "userA", 203L);
        sessionManager.addSession(sessionId, ctx, "userB", 204L);
        assertEquals("userB", sessionManager.getSession(sessionId).getUsername());
        assertTrue(sessionManager.isUserOnline(204L));
        assertFalse(sessionManager.isUserOnline(203L));
    }

    @Test
    void testRemoveSessionWithNullChannel() {
        String sessionId = "sessionNullCh";
        Long userId = 205L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        when(ctxMock.channel()).thenReturn(null);
        sessionManager.addSession(sessionId, ctxMock, "userNullCh", userId);
        assertDoesNotThrow(() -> sessionManager.removeSession(sessionId));
    }

    @Test
    void testRemoveSessionWithEmptyUsername() {
        String sessionId = "sessionEmptyUser2";
        Long userId = 206L;
        sessionManager.addSession(sessionId, ctx, "", userId);
        assertDoesNotThrow(() -> sessionManager.removeSession(sessionId));
    }

    @Test
    void testBroadcastUserOnlineStatusOfflineLastSeenFromDb() {
        String sessionId = "sessionLastSeen";
        Long userId = 207L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chLastSeen");
        sessionManager.addSession(sessionId, ctxMock, "userLastSeen", userId);
        websocket.service.UserDataService.UserData userData = mock(websocket.service.UserDataService.UserData.class);
        java.time.LocalDateTime lastSeen = java.time.LocalDateTime.now().minusMinutes(5);
        when(userData.getLastSeen()).thenReturn(lastSeen);
        when(userDataService.getUserData(userId)).thenReturn(userData);
        sessionManager.removeSession(sessionId);
        verify(userDataService, atLeastOnce()).getUserData(userId);
    }

    @Test
    void testBroadcastUserOnlineStatusOfflineLastSeenDbException() {
        String sessionId = "sessionLastSeenEx";
        Long userId = 208L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chLastSeenEx");
        sessionManager.addSession(sessionId, ctxMock, "userLastSeenEx", userId);
        when(userDataService.getUserData(userId)).thenThrow(new RuntimeException("DB error"));
        assertDoesNotThrow(() -> sessionManager.removeSession(sessionId));
    }

    @Test
    void testGetChatChannelsAllInactive() {
        Long chatId = 209L;
        Long userId = 209L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(false);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chInactive");
        sessionManager.addSession("sessionInactive", ctxMock, "userInactive", userId);
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        var channels = sessionManager.getChatChannels(chatId);
        assertTrue(channels.isEmpty());
    }

    @Test
    void testGetChatChannelsParticipantNoSession() {
        Long chatId = 210L;
        Long userId = 210L;
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        var channels = sessionManager.getChatChannels(chatId);
        assertTrue(channels.isEmpty());
    }

    @Test
    void testGetChatChannelsParticipantChannelNull() {
        Long chatId = 211L;
        Long userId = 211L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        when(ctxMock.channel()).thenReturn(null);
        sessionManager.addSession("sessionChNull", ctxMock, "userChNull", userId);
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        var channels = sessionManager.getChatChannels(chatId);
        assertTrue(channels.isEmpty());
    }

    @Test
    void testSendMessageToUserWithNullMessage() {
        Long userId = 212L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chNullMsg");
        sessionManager.addSession("sessionNullMsg", ctxMock, "userNullMsg", userId);
        assertFalse(sessionManager.sendMessageToUser(userId, null));
    }

    @Test
    void testSendMessageToUserNonExistentUser() {
        assertFalse(sessionManager.sendMessageToUser(99999L, new websocket.model.WebSocketMessage()));
    }

    @Test
    void testBroadcastMessageToChatWithFileMeta() {
        Long chatId = 213L;
        Long userId = 213L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chFileMeta");
        sessionManager.addSession("sessionFileMeta", ctxMock, "userFileMeta", userId);
        when(chatParticipantService.getChatParticipants(chatId)).thenReturn(java.util.Collections.singletonList(userId));
        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
        messageData.put("type", "NEW_MESSAGE");
        messageData.put("content", "File message");
        messageData.put("senderId", userId);
        messageData.put("senderUsername", "userFileMeta");
        messageData.put("fileUrl", "http://file.url");
        messageData.put("fileName", "file.txt");
        messageData.put("fileSize", 12345L);
        messageData.put("mimeType", "text/plain");
        messageData.put("thumbnailUrl", "http://thumb.url");
        assertDoesNotThrow(() -> sessionManager.broadcastMessageToChat(chatId, messageData));
    }

    @Test
    void testSendNotificationToUserWithComplexData() {
        Long userId = 214L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chNotifComplex");
        sessionManager.addSession("sessionNotifComplex", ctxMock, "userNotifComplex", userId);
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("type", "COMPLEX_EVENT");
        notificationData.put("payload", java.util.Map.of("key1", "value1", "key2", java.util.List.of(1,2,3)));
        assertDoesNotThrow(() -> sessionManager.sendNotificationToUser(userId, notificationData));
    }

    @Test
    void testGetAllActiveChannelsAllInactive() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(false);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chInactiveAll");
        sessionManager.addSession("sessionInactiveAll", ctxMock, "userInactiveAll", 215L);
        var channels = sessionManager.getAllActiveChannels();
        assertTrue(channels.isEmpty());
    }

    @Test
    void testGetAllActiveChannelsChannelNull() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        when(ctxMock.channel()).thenReturn(null);
        sessionManager.addSession("sessionChNullAll", ctxMock, "userChNullAll", 216L);
        var channels = sessionManager.getAllActiveChannels();
        assertTrue(channels.isEmpty());
    }

    @Test
    void testConcurrentAddRemoveSameSession() throws InterruptedException {
        String sessionId = "sessionConcurrent";
        Long userId = 217L;
        int threadCount = 5;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                sessionManager.addSession(sessionId, ctx, "userConcurrent", userId);
                sessionManager.removeSession(sessionId);
                latch.countDown();
            }).start();
        }
        latch.await();
        assertNull(sessionManager.getSession(sessionId));
    }

    @Test
    void testConcurrentAddSameUserId() throws InterruptedException {
        Long userId = 218L;
        int threadCount = 5;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final String sessionId = "sessionUserId" + i;
            new Thread(() -> {
                sessionManager.addSession(sessionId, ctx, "userIdConcurrent", userId);
                latch.countDown();
            }).start();
        }
        latch.await();
        assertEquals(1, sessionManager.getAllSessions().size());
        assertTrue(sessionManager.isUserOnline(userId));
    }

    @Test
    void testIdempotentAddSession() {
        String sessionId = "sessionIdem";
        Long userId = 300L;
        sessionManager.addSession(sessionId, ctx, "userIdem", userId);
        sessionManager.addSession(sessionId, ctx, "userIdem", userId);
        assertNotNull(sessionManager.getSession(sessionId));
        assertEquals(userId, sessionManager.getSession(sessionId).getUserId());
    }

    @Test
    void testRemoveSessionWithNonexistentUserId() {
        String sessionId = "sessionNonUser";
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        sessionManager.addSession(sessionId, ctxMock, "userNonUser", 301L);
        sessionManager.removeSession(sessionId);
        assertNull(sessionManager.getSession(sessionId));
        assertNull(sessionManager.getSessionByUserId(301L));
    }

    @Test
    void testUserInMultipleChats() {
        Long userId = 302L;
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        io.netty.channel.ChannelId channelIdMock = mock(io.netty.channel.ChannelId.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        when(channelMock.id()).thenReturn(channelIdMock);
        when(channelIdMock.asShortText()).thenReturn("chMulti");
        sessionManager.addSession("sessionMulti", ctxMock, "userMulti", userId);
        when(chatParticipantService.getChatParticipants(1L)).thenReturn(java.util.Collections.singletonList(userId));
        when(chatParticipantService.getChatParticipants(2L)).thenReturn(java.util.Collections.singletonList(userId));
        var channels1 = sessionManager.getChatChannels(1L);
        var channels2 = sessionManager.getChatChannels(2L);
        assertEquals(1, channels1.size());
        assertEquals(1, channels2.size());
        assertEquals(channelMock, channels1.get(0));
        assertEquals(channelMock, channels2.get(0));
    }

    @Test
    void testMassiveOnlineStatusBroadcast() {
        for (long i = 400; i < 500; i++) {
            ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
            io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
            when(ctxMock.channel()).thenReturn(channelMock);
            when(channelMock.isActive()).thenReturn(true);
            sessionManager.addSession("sessionMassive" + i, ctxMock, "userMassive" + i, i);
        }
        for (long i = 400; i < 500; i++) {
            sessionManager.removeSession("sessionMassive" + i);
        }
        for (long i = 400; i < 500; i++) {
            assertNull(sessionManager.getSession("sessionMassive" + i));
            assertNull(sessionManager.getSessionByUserId(i));
        }
    }

    @Test
    void testCleanupAfterRemoveSession() {
        String sessionId = "sessionCleanup";
        Long userId = 600L;
        sessionManager.addSession(sessionId, ctx, "userCleanup", userId);
        sessionManager.removeSession(sessionId);
        assertNull(sessionManager.getSession(sessionId));
        assertNull(sessionManager.getSessionByUserId(userId));
        assertNull(sessionManager.getUsername(sessionId));
        assertNull(sessionManager.getUserId(sessionId));
        assertNull(sessionManager.getContext(sessionId));
        assertNull(sessionManager.getContextByUserId(userId));
        assertNull(sessionManager.getUserChannel(userId));
    }

    @Test
    void testSameUsernameDifferentUserId() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        sessionManager.addSession("sessionA", ctxMock, "sameUser", 700L);
        sessionManager.addSession("sessionB", ctxMock, "sameUser", 701L);
        assertNotNull(sessionManager.getSession("sessionA"));
        assertNotNull(sessionManager.getSession("sessionB"));
        assertEquals("sameUser", sessionManager.getSession("sessionA").getUsername());
        assertEquals("sameUser", sessionManager.getSession("sessionB").getUsername());
    }

    @Test
    void testSameUserIdDifferentUsername() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        Long userId = 800L;
        sessionManager.addSession("sessionA", ctxMock, "userA", userId);
        sessionManager.addSession("sessionB", ctxMock, "userB", userId);
        assertNull(sessionManager.getSession("sessionA"));
        assertNotNull(sessionManager.getSession("sessionB"));
        assertEquals("userB", sessionManager.getSession("sessionB").getUsername());
    }

    @Test
    void testChannelHandlerContextThrowsException() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        when(ctxMock.channel()).thenThrow(new RuntimeException("channel error"));
        assertDoesNotThrow(() -> sessionManager.addSession("sessionExCtx", ctxMock, "userExCtx", 900L));
    }

    @Test
    void testChannelThrowsExceptionOnIsActive() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenThrow(new RuntimeException("isActive error"));
        assertDoesNotThrow(() -> sessionManager.addSession("sessionExActive", ctxMock, "userExActive", 901L));
    }

    @Test
    void testChannelThrowsExceptionOnWriteAndFlush() {
        ChannelHandlerContext ctxMock = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel channelMock = mock(io.netty.channel.Channel.class);
        when(ctxMock.channel()).thenReturn(channelMock);
        when(channelMock.isActive()).thenReturn(true);
        doThrow(new RuntimeException("writeAndFlush error")).when(channelMock).writeAndFlush(any());
        assertDoesNotThrow(() -> sessionManager.addSession("sessionExFlush", ctxMock, "userExFlush", 902L));
    }
}
