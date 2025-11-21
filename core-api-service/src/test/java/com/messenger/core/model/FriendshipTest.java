package com.messenger.core.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class FriendshipTest {
    @Test
    void testPrePersistAndPreUpdate() {
        Friendship friendship = new Friendship();
        friendship.onCreate();
        assertNotNull(friendship.getCreatedAt());
        assertNotNull(friendship.getUpdatedAt());
        LocalDateTime created = friendship.getCreatedAt();
        friendship.onUpdate();
        assertTrue(friendship.getUpdatedAt().isAfter(created) || friendship.getUpdatedAt().isEqual(created));
    }

    @Test
    void testAllArgsConstructorAndGettersSetters() {
        Friendship friendship = new Friendship(1L, null, null, Friendship.FriendshipStatus.ACCEPTED, LocalDateTime.now(), LocalDateTime.now());
        assertEquals(1L, friendship.getId());
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, friendship.getStatus());
        assertNotNull(friendship.getCreatedAt());
        assertNotNull(friendship.getUpdatedAt());
    }

    @Test
    void testEnumValues() {
        assertEquals(Friendship.FriendshipStatus.PENDING, Friendship.FriendshipStatus.valueOf("PENDING"));
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, Friendship.FriendshipStatus.valueOf("ACCEPTED"));
        assertEquals(Friendship.FriendshipStatus.REJECTED, Friendship.FriendshipStatus.valueOf("REJECTED"));
        assertEquals(Friendship.FriendshipStatus.BLOCKED, Friendship.FriendshipStatus.valueOf("BLOCKED"));
    }

    @Test
    void testEqualsWithNull() {
        Friendship f = new Friendship();
        assertNotEquals(f, null);
    }

    @Test
    void testEqualsWithOtherClass() {
        Friendship f = new Friendship();
        assertNotEquals(f, "string");
    }

    @Test
    void testEqualsWithSelf() {
        Friendship f = new Friendship();
        assertEquals(f, f);
    }

    @Test
    void testEqualsWithDifferentFields() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(2L);
        assertNotEquals(f1, f2);
    }

    @Test
    void testEqualsWithSameFields() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(1L);
        f1.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        f2.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        assertEquals(f1, f2);
    }

    @Test
    void testEqualsWithDifferentStatus() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(1L);
        f1.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        f2.setStatus(Friendship.FriendshipStatus.PENDING);
        assertNotEquals(f1, f2);
    }

    @Test
    void testEqualsWithDifferentRequester() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(1L);
        User u1 = new User(); u1.setId(10L);
        User u2 = new User(); u2.setId(11L);
        f1.setRequester(u1);
        f2.setRequester(u2);
        assertEquals(f1, f2);
    }

    @Test
    void testEqualsWithDifferentReceiver() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(1L);
        User u1 = new User(); u1.setId(10L);
        User u2 = new User(); u2.setId(11L);
        f1.setReceiver(u1);
        f2.setReceiver(u2);
        assertEquals(f1, f2);
    }

    @Test
    void testEqualsWithAllFieldsSame() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(1L);
        f1.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        f2.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        User u = new User(); u.setId(10L);
        f1.setRequester(u); f2.setRequester(u);
        f1.setReceiver(u); f2.setReceiver(u);
        assertEquals(f1, f2);
    }

    @Test
    void testEqualsWithNullFields() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(null);
        f2.setId(null);
        f1.setStatus(null);
        f2.setStatus(null);
        assertEquals(f1, f2);
    }

    @Test
    void testEqualsWithSameIdDifferentFields() {
        Friendship f1 = new Friendship();
        Friendship f2 = new Friendship();
        f1.setId(1L);
        f2.setId(1L);
        f1.setStatus(null);
        f2.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        assertNotEquals(f1, f2);
    }
}
