package com.messenger.core.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserDtoUpdateProfileRequestTest {
    @Test
    void testEqualsAndHashCode_sameValues() {
        UserDto.UpdateProfileRequest r1 = new UserDto.UpdateProfileRequest("user", "email@mail.com", "fn", "ln", "url", "bio");
        UserDto.UpdateProfileRequest r2 = new UserDto.UpdateProfileRequest("user", "email@mail.com", "fn", "ln", "url", "bio");
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentValues() {
        UserDto.UpdateProfileRequest r1 = new UserDto.UpdateProfileRequest("user1", "email1@mail.com", "fn1", "ln1", "url1", "bio1");
        UserDto.UpdateProfileRequest r2 = new UserDto.UpdateProfileRequest("user2", "email2@mail.com", "fn2", "ln2", "url2", "bio2");
        assertNotEquals(r1, r2);
        assertNotEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_partialDifference() {
        UserDto.UpdateProfileRequest r1 = new UserDto.UpdateProfileRequest("user", "email@mail.com", "fn", "ln", "url", "bio");
        UserDto.UpdateProfileRequest r2 = new UserDto.UpdateProfileRequest("user", "email@mail.com", "fn", "ln", "url", "bio2");
        assertNotEquals(r1, r2);
        assertNotEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_nullFields() {
        UserDto.UpdateProfileRequest r1 = new UserDto.UpdateProfileRequest(null, null, null, null, null, null);
        UserDto.UpdateProfileRequest r2 = new UserDto.UpdateProfileRequest(null, null, null, null, null, null);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_mixedNullFields() {
        UserDto.UpdateProfileRequest r1 = new UserDto.UpdateProfileRequest("user", null, null, null, null, null);
        UserDto.UpdateProfileRequest r2 = new UserDto.UpdateProfileRequest("user", null, null, null, null, null);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        UserDto.UpdateProfileRequest r3 = new UserDto.UpdateProfileRequest(null, null, null, null, null, null);
        assertNotEquals(r1, r3);
    }

    @Test
    void testEquals_nullAndOtherType() {
        UserDto.UpdateProfileRequest r = new UserDto.UpdateProfileRequest("user", "email@mail.com", "fn", "ln", "url", "bio");
        assertNotEquals(null, r);
        assertNotEquals("string", r);
    }

    @Test
    void testEqualsAndHashCode_emptyFields() {
        UserDto.UpdateProfileRequest r1 = new UserDto.UpdateProfileRequest();
        UserDto.UpdateProfileRequest r2 = new UserDto.UpdateProfileRequest();
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}

