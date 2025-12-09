package com.messenger.websocket.model;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.messenger.websocket.model.UserSession;

import static org.junit.jupiter.api.Assertions.*;

public class UserSessionTest {
    @Test
    void testAllArgsConstructorAndGetters() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession session = new UserSession("sess1", ctx, "user1", 1L, 2L);
        assertEquals("sess1", session.getSessionId());
        assertEquals(ctx, session.getContext());
        assertEquals("user1", session.getUsername());
        assertEquals(1L, session.getUserId());
        assertEquals(2L, session.getCurrentChatId());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        UserSession session = new UserSession();
        session.setSessionId("sess2");
        session.setContext(null);
        session.setUsername("user2");
        session.setUserId(2L);
        session.setCurrentChatId(3L);
        assertEquals("sess2", session.getSessionId());
        assertNull(session.getContext());
        assertEquals("user2", session.getUsername());
        assertEquals(2L, session.getUserId());
        assertEquals(3L, session.getCurrentChatId());
    }

    @Test
    void testSessionManagerConstructor() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession session = new UserSession("sess3", ctx, "user3", 3L);
        assertEquals("sess3", session.getSessionId());
        assertEquals(ctx, session.getContext());
        assertEquals("user3", session.getUsername());
        assertEquals(3L, session.getUserId());
        assertNull(session.getCurrentChatId());
    }

    @Test
    void testEqualsReflexive() {
        UserSession session = new UserSession();
        assertEquals(session, session);
    }

    @Test
    void testEqualsNull() {
        UserSession session = new UserSession();
        assertNotEquals(session, null);
    }

    @Test
    void testEqualsOtherClass() {
        UserSession session = new UserSession();
        Object other = new Object();
        assertNotEquals(session, other);
    }

    @Test
    void testEqualsAllFieldsEqual() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx, "user", 1L, 2L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsDifferentSessionId() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess1", ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess2", ctx, "user", 1L, 2L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsDifferentContext() {
        ChannelHandlerContext ctx1 = Mockito.mock(ChannelHandlerContext.class);
        ChannelHandlerContext ctx2 = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx1, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx2, "user", 1L, 2L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsDifferentUsername() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user1", 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx, "user2", 1L, 2L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsDifferentUserId() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx, "user", 2L, 2L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsDifferentCurrentChatId() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx, "user", 1L, 3L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsNullFields() {
        UserSession s1 = new UserSession();
        UserSession s2 = new UserSession();
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsDefaultValues() {
        UserSession s1 = new UserSession();
        s1.setSessionId("");
        s1.setUsername("");
        s1.setUserId(0L);
        s1.setCurrentChatId(0L);
        UserSession s2 = new UserSession();
        s2.setSessionId("");
        s2.setUsername("");
        s2.setUserId(0L);
        s2.setCurrentChatId(0L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsEdgeValues() {
        UserSession s1 = new UserSession("", null, "", Long.MAX_VALUE, Long.MIN_VALUE);
        UserSession s2 = new UserSession("", null, "", Long.MAX_VALUE, Long.MIN_VALUE);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsNullSessionId() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession(null, ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession(null, ctx, "user", 1L, 2L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsNullUsername() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, null, 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx, null, 1L, 2L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsNullCurrentChatId() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user", 1L, null);
        UserSession s2 = new UserSession("sess", ctx, "user", 1L, null);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsNullUserId() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user", null, 2L);
        UserSession s2 = new UserSession("sess", ctx, "user", null, 2L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsNullContext() {
        UserSession s1 = new UserSession("sess", null, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess", null, "user", 1L, 2L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsDifferentNullCombinations() {
        UserSession s1 = new UserSession(null, null, null, null, null);
        UserSession s2 = new UserSession("sess", null, null, null, null);
        assertNotEquals(s1, s2);
        s2 = new UserSession(null, Mockito.mock(ChannelHandlerContext.class), null, null, null);
        assertNotEquals(s1, s2);
        s2 = new UserSession(null, null, "user", null, null);
        assertNotEquals(s1, s2);
        s2 = new UserSession(null, null, null, 1L, null);
        assertNotEquals(s1, s2);
        s2 = new UserSession(null, null, null, null, 2L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsStringReferenceVsValue() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        String s1str = new String("sess");
        String s2str = new String("sess");
        UserSession s1 = new UserSession(s1str, ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession(s2str, ctx, "user", 1L, 2L);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testEqualsLongVsInt() {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        UserSession s1 = new UserSession("sess", ctx, "user", 1L, 2L);
        UserSession s2 = new UserSession("sess", ctx, "user", 2L, 2L);
        assertNotEquals(s1, s2);
    }
}
