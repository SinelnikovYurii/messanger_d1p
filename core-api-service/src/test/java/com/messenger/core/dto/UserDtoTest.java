package com.messenger.core.dto;

import com.messenger.core.model.Friendship;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserDtoTest {
    @Test
    void testUserSearchResultEqualsAndHashCode_sameValues() {
        UserDto.UserSearchResult u1 = new UserDto.UserSearchResult(1L, "user", "fn", "ln", "url", true, Friendship.FriendshipStatus.ACCEPTED, true);
        UserDto.UserSearchResult u2 = new UserDto.UserSearchResult(1L, "user", "fn", "ln", "url", true, Friendship.FriendshipStatus.ACCEPTED, true);
        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    void testUserSearchResultEqualsAndHashCode_differentValues() {
        UserDto.UserSearchResult u1 = new UserDto.UserSearchResult(1L, "user1", "fn1", "ln1", "url1", true, Friendship.FriendshipStatus.ACCEPTED, true);
        UserDto.UserSearchResult u2 = new UserDto.UserSearchResult(2L, "user2", "fn2", "ln2", "url2", false, Friendship.FriendshipStatus.PENDING, false);
        assertNotEquals(u1, u2);
        assertNotEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    void testUserSearchResultEquals_nullAndOtherType() {
        UserDto.UserSearchResult u = new UserDto.UserSearchResult(1L, "user", "fn", "ln", "url", true, Friendship.FriendshipStatus.ACCEPTED, true);
        assertNotEquals(null, u);
        assertNotEquals("string", u);
    }

    @Test
    void testUserSearchResultEqualsAndHashCode_emptyFields() {
        UserDto.UserSearchResult u1 = new UserDto.UserSearchResult();
        UserDto.UserSearchResult u2 = new UserDto.UserSearchResult();
        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
    }
}
