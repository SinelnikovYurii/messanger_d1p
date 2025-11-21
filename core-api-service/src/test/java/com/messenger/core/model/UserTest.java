package com.messenger.core.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    @Test
    void testPrePersistAndPreUpdate() {
        User user = new User();
        user.onCreate();
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        LocalDateTime created = user.getCreatedAt();
        user.onUpdate();
        assertTrue(user.getUpdatedAt().isAfter(created) || user.getUpdatedAt().isEqual(created));
    }

    @Test
    void testAllArgsConstructorAndGettersSetters() {
        User user = new User(1L, "username", "email", "password", "first", "last", "pic", "bio", false, null, null, null, null, null, null, null);
        assertEquals(1L, user.getId());
        assertEquals("username", user.getUsername());
        assertEquals("email", user.getEmail());
        assertEquals("password", user.getPassword());
        assertEquals("first", user.getFirstName());
        assertEquals("last", user.getLastName());
        assertEquals("pic", user.getProfilePictureUrl());
        assertEquals("bio", user.getBio());
        assertFalse(user.getIsOnline());
        assertNull(user.getLastSeen());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
        assertNull(user.getChats());
        assertNull(user.getSentMessages());
        assertNull(user.getSentFriendRequests());
        assertNull(user.getReceivedFriendRequests());
    }

    @Test
    void testEqualsAndHashCode() {
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("username");
        user1.setEmail("email");
        user1.setPassword("password");
        user1.setChats(new HashSet<>());
        user1.setSentMessages(new ArrayList<>());
        user1.setSentFriendRequests(new HashSet<>());
        user1.setReceivedFriendRequests(new HashSet<>());

        User user2 = new User();
        user2.setId(1L);
        user2.setUsername("username");
        user2.setEmail("email");
        user2.setPassword("password");
        user2.setChats(new HashSet<>());
        user2.setSentMessages(new ArrayList<>());
        user2.setSentFriendRequests(new HashSet<>());
        user2.setReceivedFriendRequests(new HashSet<>());

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testEqualsWithNull() {
        User user = new User();
        assertNotEquals(user, null);
    }

    @Test
    void testEqualsWithOtherClass() {
        User user = new User();
        assertNotEquals(user, "string");
    }

    @Test
    void testEqualsWithSelf() {
        User user = new User();
        assertEquals(user, user);
    }

    @Test
    void testEqualsWithDifferentFields() {
        User user1 = new User();
        User user2 = new User();
        user1.setId(1L);
        user2.setId(2L);
        assertNotEquals(user1, user2);
    }

    @Test
    void testEqualsWithSameFields() {
        User user1 = new User();
        User user2 = new User();
        user1.setId(1L);
        user2.setId(1L);
        user1.setUsername("abc");
        user2.setUsername("abc");
        user1.setIsOnline(true);
        user2.setIsOnline(true);
        assertEquals(user1, user2);
    }

    @Test
    void testEqualsWithDifferentUsername() {
        User u1 = new User();
        User u2 = new User();
        u1.setId(1L);
        u2.setId(1L);
        u1.setUsername("a");
        u2.setUsername("b");
        assertNotEquals(u1, u2);
    }

    @Test
    void testEqualsWithDifferentEmail() {
        User u1 = new User();
        User u2 = new User();
        u1.setId(1L);
        u2.setId(1L);
        u1.setEmail("a@a");
        u2.setEmail("b@b");
        assertNotEquals(u1, u2);
    }

    @Test
    void testEqualsWithDifferentPassword() {
        User u1 = new User();
        User u2 = new User();
        u1.setId(1L);
        u2.setId(1L);
        u1.setPassword("123");
        u2.setPassword("456");
        assertNotEquals(u1, u2);
    }

    @Test
    void testEqualsWithAllFieldsSame() {
        User u1 = new User();
        User u2 = new User();
        u1.setId(1L);
        u2.setId(1L);
        u1.setUsername("abc");
        u2.setUsername("abc");
        u1.setEmail("mail");
        u2.setEmail("mail");
        u1.setPassword("pass");
        u2.setPassword("pass");
        assertEquals(u1, u2);
    }

    @Test
    void testEqualsWithNullFields() {
        User u1 = new User();
        User u2 = new User();
        u1.setId(null);
        u2.setId(null);
        u1.setUsername(null);
        u2.setUsername(null);
        assertEquals(u1, u2);
    }

    @Test
    void testEqualsWithSameIdDifferentFields() {
        User u1 = new User();
        User u2 = new User();
        u1.setId(1L);
        u2.setId(1L);
        u1.setUsername(null);
        u2.setUsername("abc");
        assertNotEquals(u1, u2);
    }
}
