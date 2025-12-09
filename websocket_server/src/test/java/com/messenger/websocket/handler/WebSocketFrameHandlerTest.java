package com.messenger.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import com.messenger.websocket.handler.WebSocketFrameHandler;
import com.messenger.websocket.model.MessageType;
import com.messenger.websocket.model.WebSocketMessage;
import com.messenger.websocket.service.JwtAuthService;
import com.messenger.websocket.service.SessionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WebSocketFrameHandlerTest {
    private JwtAuthService jwtAuthService;
    private ObjectMapper objectMapper;
    private SessionManager sessionManager;
    private KafkaTemplate<String, String> kafkaTemplate;
    private TestableWebSocketFrameHandler testableHandler;
    private ChannelHandlerContext ctx;

    static class TestableWebSocketFrameHandler extends WebSocketFrameHandler {
        public TestableWebSocketFrameHandler(JwtAuthService jwtAuthService, ObjectMapper objectMapper, SessionManager sessionManager, KafkaTemplate<String, String> kafkaTemplate) {
            super(jwtAuthService, objectMapper, sessionManager, kafkaTemplate);
        }
        @Override
        public void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
            super.channelRead0(ctx, frame);
        }
    }

    @BeforeEach
    void setUp() {
        jwtAuthService = mock(JwtAuthService.class);
        objectMapper = mock(ObjectMapper.class);
        sessionManager = mock(SessionManager.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        testableHandler = new TestableWebSocketFrameHandler(jwtAuthService, objectMapper, sessionManager, kafkaTemplate);
        ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(mock(io.netty.channel.Channel.class));
        when(ctx.channel().id()).thenReturn(mock(io.netty.channel.ChannelId.class));
        when(ctx.channel().id().asShortText()).thenReturn("sessionId");
    }

    @Test
    void testChannelRead0ValidTextFrameAuth() throws Exception {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.AUTH);
        when(objectMapper.readValue(anyString(), eq(WebSocketMessage.class))).thenReturn(msg);
        TextWebSocketFrame frame = new TextWebSocketFrame("{\"type\":\"AUTH\"}");
        when(sessionManager.isAuthenticated(anyString())).thenReturn(false);
        testableHandler.channelRead0(ctx, frame);
        verify(objectMapper).readValue(anyString(), eq(WebSocketMessage.class));
    }

    @Test
    void testChannelRead0ValidTextFrameChatMessage() throws Exception {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.CHAT_MESSAGE);
        when(objectMapper.readValue(anyString(), eq(WebSocketMessage.class))).thenReturn(msg);
        TextWebSocketFrame frame = new TextWebSocketFrame("{\"type\":\"CHAT_MESSAGE\"}");
        when(sessionManager.isAuthenticated(anyString())).thenReturn(true);
        when(sessionManager.getUsername(anyString())).thenReturn("user");
        when(sessionManager.getUserId(anyString())).thenReturn(1L);
        testableHandler.channelRead0(ctx, frame);
        verify(objectMapper).readValue(anyString(), eq(WebSocketMessage.class));
    }

    @Test
    void testChannelRead0ValidTextFramePing() throws Exception {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.PING);
        when(objectMapper.readValue(anyString(), eq(WebSocketMessage.class))).thenReturn(msg);
        TextWebSocketFrame frame = new TextWebSocketFrame("{\"type\":\"PING\"}");
        testableHandler.channelRead0(ctx, frame);
        verify(objectMapper).readValue(anyString(), eq(WebSocketMessage.class));
    }

    @Test
    void testChannelRead0UnknownType() throws Exception {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(null);
        when(objectMapper.readValue(anyString(), eq(WebSocketMessage.class))).thenReturn(msg);
        TextWebSocketFrame frame = new TextWebSocketFrame("{\"type\":\"UNKNOWN\"}");
        testableHandler.channelRead0(ctx, frame);
        verify(objectMapper).readValue(anyString(), eq(WebSocketMessage.class));
    }

    @Test
    void testChannelRead0InvalidJson() throws Exception {
        when(objectMapper.readValue(anyString(), eq(WebSocketMessage.class))).thenThrow(new RuntimeException("Invalid JSON"));
        TextWebSocketFrame frame = new TextWebSocketFrame("invalid json");
        testableHandler.channelRead0(ctx, frame);
        verify(objectMapper).readValue(anyString(), eq(WebSocketMessage.class));
    }

    @Test
    void testChannelRead0NotTextWebSocketFrame() throws Exception {
        WebSocketFrame frame = mock(WebSocketFrame.class);
        testableHandler.channelRead0(ctx, frame);
        // Should not call objectMapper.readValue
        verify(objectMapper, never()).readValue(anyString(), eq(WebSocketMessage.class));
    }

    @Test
    void testChannelActive() throws Exception {
        testableHandler.channelActive(ctx);
        // Just check that no exception is thrown
    }

    @Test
    void testUserEventTriggeredHandshakeCompleteWithToken() throws Exception {
        when(ctx.channel().attr(any())).thenReturn(mock(io.netty.util.Attribute.class));
        when(ctx.channel().attr(any()).get()).thenReturn("token");
        when(jwtAuthService.validateToken(anyString())).thenReturn(true);
        when(jwtAuthService.getUsernameFromToken(anyString())).thenReturn("user");
        when(jwtAuthService.getUserIdFromToken(anyString())).thenReturn(1L);
        doNothing().when(sessionManager).addSession(anyString(), any(), anyString(), anyLong());
        testableHandler.userEventTriggered(ctx, WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);
        verify(jwtAuthService).validateToken(anyString());
    }

    @Test
    void testUserEventTriggeredHandshakeCompleteNoToken() throws Exception {
        when(ctx.channel().attr(any())).thenReturn(mock(io.netty.util.Attribute.class));
        when(ctx.channel().attr(any()).get()).thenReturn(null);
        testableHandler.userEventTriggered(ctx, WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);
        // Should call ctx.close()
        verify(ctx).close();
    }

    @Test
    void testUserEventTriggeredOtherEvent() throws Exception {
        testableHandler.userEventTriggered(ctx, "OTHER_EVENT");
        // Just check that no exception is thrown
    }

    @Test
    void testChannelInactive() throws Exception {
        testableHandler.channelInactive(ctx);
        verify(sessionManager).removeSession(anyString());
    }

    @Test
    void testExceptionCaught() throws Exception {
        Throwable t = new RuntimeException("Test error");
        testableHandler.exceptionCaught(ctx, t);
        verify(ctx).close();
    }
}
