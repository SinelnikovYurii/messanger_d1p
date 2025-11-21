package com.messenger.core.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class MessageReadStatusTest {
    @Test
    void testPrePersistSetsReadAtIfNull() {
        MessageReadStatus status = new MessageReadStatus();
        status.setReadAt(null);
        status.onCreate();
        assertNotNull(status.getReadAt());
    }

    @Test
    void testPrePersistDoesNotOverrideReadAt() {
        MessageReadStatus status = new MessageReadStatus();
        LocalDateTime now = LocalDateTime.of(2023, 1, 1, 0, 0);
        status.setReadAt(now);
        status.onCreate();
        assertEquals(now, status.getReadAt());
    }

    @Test
    void testAllArgsConstructorAndGettersSetters() {
        MessageReadStatus status = new MessageReadStatus(1L, null, null, LocalDateTime.now());
        assertEquals(1L, status.getId());
        assertNotNull(status.getReadAt());
    }

    @Test
    void testEqualsWithNull() {
        MessageReadStatus s = new MessageReadStatus();
        assertNotEquals(s, null);
    }

    @Test
    void testEqualsWithOtherClass() {
        MessageReadStatus s = new MessageReadStatus();
        assertNotEquals(s, "string");
    }

    @Test
    void testEqualsWithSelf() {
        MessageReadStatus s = new MessageReadStatus();
        assertEquals(s, s);
    }

    @Test
    void testEqualsWithDifferentFields() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(2L);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsWithSameFields() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        s1.setReadAt(java.time.LocalDateTime.now());
        s2.setReadAt(s1.getReadAt());
        assertEquals(s1, s2);
    }

    @Test
    void testEqualsWithDifferentReadAt() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        s1.setReadAt(LocalDateTime.of(2023, 1, 1, 0, 0));
        s2.setReadAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsWithDifferentMessage() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        Message m1 = new Message(); m1.setId(10L);
        Message m2 = new Message(); m2.setId(11L);
        s1.setMessage(m1);
        s2.setMessage(m2);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsWithDifferentUser() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        User u1 = new User(); u1.setId(100L);
        User u2 = new User(); u2.setId(101L);
        s1.setUser(u1);
        s2.setUser(u2);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEqualsWithAllFieldsSame() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        Message m = new Message(); m.setId(10L);
        User u = new User(); u.setId(100L);
        LocalDateTime dt = LocalDateTime.of(2023, 1, 1, 0, 0);
        s1.setMessage(m); s2.setMessage(m);
        s1.setUser(u); s2.setUser(u);
        s1.setReadAt(dt); s2.setReadAt(dt);
        assertEquals(s1, s2);
    }

    @Test
    void testEqualsWithNullId() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(null);
        s2.setId(null);
        assertEquals(s1, s2);
    }

    @Test
    void testEqualsWithNullUserMessageReadAt() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        s1.setUser(null); s2.setUser(null);
        s1.setMessage(null); s2.setMessage(null);
        s1.setReadAt(null); s2.setReadAt(null);
        assertEquals(s1, s2);
    }

    @Test
    void testEqualsWithSameIdDifferentFields() {
        MessageReadStatus s1 = new MessageReadStatus();
        MessageReadStatus s2 = new MessageReadStatus();
        s1.setId(1L);
        s2.setId(1L);
        s1.setUser(null); s2.setUser(new User());
        assertNotEquals(s1, s2);
    }
}
