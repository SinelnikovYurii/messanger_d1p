package com.messenger.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {
    @Test
    void testDefaultConstructor() {
        CustomUserDetails user = new CustomUserDetails();
        assertNull(user.getId());
        assertNull(user.getUsername());
        assertNull(user.getPassword());
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertEquals(Collections.emptyList(), user.getAuthorities());
    }

    @Test
    void testAllArgsConstructor() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails(1L, "user", "pass", false, false, false, false, authorities);
        assertEquals(1L, user.getId());
        assertEquals("user", user.getUsername());
        assertEquals("pass", user.getPassword());
        assertFalse(user.isEnabled());
        assertFalse(user.isAccountNonExpired());
        assertFalse(user.isAccountNonLocked());
        assertFalse(user.isCredentialsNonExpired());
        assertEquals(authorities, user.getAuthorities());
    }

    @Test
    void testIdUsernameConstructor() {
        CustomUserDetails user = new CustomUserDetails(2L, "testuser");
        assertEquals(2L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertNull(user.getPassword());
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertEquals(Collections.emptyList(), user.getAuthorities());
    }

    @Test
    void testAuthoritiesWithEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SimpleGrantedAuthority("");
        });
    }

    @Test
    void testAuthoritiesNull() {
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(null);
        assertNull(user.getAuthorities());
    }

    @Test
    void testEqualsAndHashCode() {
        CustomUserDetails user1 = new CustomUserDetails(1L, "user");
        CustomUserDetails user2 = new CustomUserDetails(1L, "user");
        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testSettersAndGetters() {
        CustomUserDetails user = new CustomUserDetails();
        user.setId(10L);
        user.setUsername("setterUser");
        user.setPassword("setterPass");
        user.setEnabled(false);
        user.setAccountNonExpired(false);
        user.setAccountNonLocked(false);
        user.setCredentialsNonExpired(false);
        user.setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertEquals(10L, user.getId());
        assertEquals("setterUser", user.getUsername());
        assertEquals("setterPass", user.getPassword());
        assertFalse(user.isEnabled());
        assertFalse(user.isAccountNonExpired());
        assertFalse(user.isAccountNonLocked());
        assertFalse(user.isCredentialsNonExpired());
        assertEquals(1, user.getAuthorities().size());
        assertEquals("ROLE_ADMIN", user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void testAuthoritiesTypeSafety() {
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        for (GrantedAuthority authority : user.getAuthorities()) {
            assertTrue(authority instanceof GrantedAuthority);
        }
    }

    @Test
    void testToString() {
        CustomUserDetails user = new CustomUserDetails(5L, "toStringUser");
        String str = user.toString();
        assertTrue(str.contains("toStringUser"));
        assertTrue(str.contains("5"));
    }

    @Test
    void testNullValues() {
        CustomUserDetails user = new CustomUserDetails();
        user.setUsername(null);
        user.setPassword(null);
        user.setAuthorities(null);
        assertNull(user.getUsername());
        assertNull(user.getPassword());
        assertNull(user.getAuthorities());
    }

    @Test
    void testFlagsAllFalse() {
        CustomUserDetails user = new CustomUserDetails(1L, "user", "pass", false, false, false, false, Collections.emptyList());
        assertFalse(user.isEnabled());
        assertFalse(user.isAccountNonExpired());
        assertFalse(user.isAccountNonLocked());
        assertFalse(user.isCredentialsNonExpired());
    }

    @Test
    void testAuthoritiesImmutableList() {
        List<GrantedAuthority> immutable = Collections.unmodifiableList(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(immutable);
        assertThrows(UnsupportedOperationException.class, () -> {
            ((List<GrantedAuthority>) user.getAuthorities()).add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        });
    }

    @Test
    void testDefaultAuthoritiesIsEmptyList() {
        CustomUserDetails user = new CustomUserDetails();
        assertNotNull(user.getAuthorities());
        assertTrue(user.getAuthorities().isEmpty());
    }

    @Test
    void testEqualsWithDifferentAuthorities() {
        CustomUserDetails user1 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        CustomUserDetails user2 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertNotEquals(user1, user2);
    }

    @Test
    void testHashCodeWithDifferentAuthorities() {
        CustomUserDetails user1 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        CustomUserDetails user2 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertNotEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testIdUsernameConstructorDefaults() {
        CustomUserDetails user = new CustomUserDetails(99L, "defaultUser");
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertEquals(Collections.emptyList(), user.getAuthorities());
    }

    @Test
    void testEmptyUsernameAndPassword() {
        CustomUserDetails user = new CustomUserDetails();
        user.setUsername("");
        user.setPassword("");
        assertEquals("", user.getUsername());
        assertEquals("", user.getPassword());
    }

    @Test
    void testAuthoritiesAcceptsEmptyList() {
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(Collections.emptyList());
        assertNotNull(user.getAuthorities());
        assertTrue(user.getAuthorities().isEmpty());
    }

    @Test
    void testFlagsAffectUserDetailsMethods() {
        CustomUserDetails user = new CustomUserDetails();
        user.setEnabled(false);
        user.setAccountNonExpired(false);
        user.setAccountNonLocked(false);
        user.setCredentialsNonExpired(false);
        assertFalse(user.isEnabled());
        assertFalse(user.isAccountNonExpired());
        assertFalse(user.isAccountNonLocked());
        assertFalse(user.isCredentialsNonExpired());
    }

    @Test
    void testAuthoritiesAsSet() {
        var set = Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(set);
        assertEquals(set, user.getAuthorities());
    }

    @Test
    void testAuthoritiesMultipleRoles() {
        var roles = List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(roles);
        assertEquals(2, user.getAuthorities().size());
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testEqualsHashCodeAuthoritiesNull() {
        CustomUserDetails user1 = new CustomUserDetails(1L, "user");
        CustomUserDetails user2 = new CustomUserDetails(1L, "user");
        user1.setAuthorities(null);
        user2.setAuthorities(null);
        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testDefaultFlagsTrue() {
        CustomUserDetails user = new CustomUserDetails();
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
    }

    @Test
    void testSetAuthoritiesUpdatesValue() {
        CustomUserDetails user = new CustomUserDetails();
        var roles1 = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var roles2 = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        user.setAuthorities(roles1);
        assertEquals(roles1, user.getAuthorities());
        user.setAuthorities(roles2);
        assertEquals(roles2, user.getAuthorities());
    }

    @Test
    void testMinimalIdOnly() {
        CustomUserDetails user = new CustomUserDetails();
        user.setId(123L);
        assertEquals(123L, user.getId());
        assertNull(user.getUsername());
    }

    @Test
    void testMinimalUsernameOnly() {
        CustomUserDetails user = new CustomUserDetails();
        user.setUsername("onlyUsername");
        assertEquals("onlyUsername", user.getUsername());
        assertNull(user.getId());
    }

    @Test
    void testAuthoritiesWithNullElement() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        authorities.add(null);
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(authorities);
        assertEquals(2, user.getAuthorities().size());
        assertNull(((List<GrantedAuthority>)user.getAuthorities()).get(1));
    }

    @Test
    void testEqualsHashCodeDifferentFlags() {
        CustomUserDetails user1 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        CustomUserDetails user2 = new CustomUserDetails(1L, "user", "pass", false, false, false, false, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertNotEquals(user1, user2);
        assertNotEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testAuthoritiesWithDuplicateRoles() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(authorities);
        assertEquals(2, user.getAuthorities().size());
    }

    @Test
    void testAuthoritiesWithCustomGrantedAuthority() {
        GrantedAuthority custom = new GrantedAuthority() {
            @Override
            public String getAuthority() {
                return "CUSTOM_ROLE";
            }
        };
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(List.of(custom));
        assertEquals("CUSTOM_ROLE", user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void testEqualsHashCodeDifferentIdUsername() {
        CustomUserDetails user1 = new CustomUserDetails(1L, "user1");
        CustomUserDetails user2 = new CustomUserDetails(2L, "user2");
        assertNotEquals(user1, user2);
        assertNotEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testAuthoritiesWithEmptyAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleGrantedAuthority(""));
    }

    @Test
    void testAuthoritiesWithWhitespaceAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleGrantedAuthority("   "));
    }

    @Test
    void testAuthoritiesOnlyNull() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(null);
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(authorities);
        assertEquals(1, user.getAuthorities().size());
        assertNull(((List<GrantedAuthority>)user.getAuthorities()).get(0));
    }

    @Test
    void testAuthoritiesWithLinkedList() {
        var linkedList = new java.util.LinkedList<GrantedAuthority>();
        linkedList.add(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(linkedList);
        assertEquals(linkedList, user.getAuthorities());
    }

    @Test
    void testAuthoritiesWithHashSet() {
        var hashSet = new java.util.HashSet<GrantedAuthority>();
        hashSet.add(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(hashSet);
        assertEquals(hashSet, user.getAuthorities());
    }

    @Test
    void testSetAuthoritiesNullDoesNotAffectOtherFields() {
        CustomUserDetails user = new CustomUserDetails(1L, "user");
        user.setAuthorities(null);
        assertNull(user.getAuthorities());
        assertEquals(1L, user.getId());
        assertEquals("user", user.getUsername());
    }

    @Test
    void testSetAuthoritiesSingleElement() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SINGLE"));
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(authorities);
        assertEquals(1, user.getAuthorities().size());
        assertEquals("ROLE_SINGLE", user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void testSetAuthoritiesDifferentImplementations() {
        GrantedAuthority custom = () -> "CUSTOM_ROLE";
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"), custom);
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(authorities);
        assertEquals(2, user.getAuthorities().size());
        assertTrue(user.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        assertTrue(user.getAuthorities().stream().anyMatch(a -> "CUSTOM_ROLE".equals(a.getAuthority())));
    }

    @Test
    void testToStringWithAuthorities() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user = new CustomUserDetails(1L, "user", "pass", true, true, true, true, authorities);
        String str = user.toString();
        assertTrue(str.contains("ROLE_USER"));
        assertTrue(str.contains("user"));
        assertTrue(str.contains("pass"));
    }

    @Test
    void testEqualsHashCodeDifferentCollectionTypes() {
        List<GrantedAuthority> list = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        java.util.LinkedList<GrantedAuthority> linkedList = new java.util.LinkedList<>();
        linkedList.add(new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user1 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, list);
        CustomUserDetails user2 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, linkedList);
        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testEqualsHashCodeDifferentOrder() {
        List<GrantedAuthority> list1 = List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        List<GrantedAuthority> list2 = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
        CustomUserDetails user1 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, list1);
        CustomUserDetails user2 = new CustomUserDetails(1L, "user", "pass", true, true, true, true, list2);
        assertNotEquals(user1, user2);
        assertNotEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testSetAuthoritiesEmptyCollection() {
        CustomUserDetails user = new CustomUserDetails();
        user.setAuthorities(Collections.emptyList());
        assertNotNull(user.getAuthorities());
        assertTrue(user.getAuthorities().isEmpty());
    }

    @Test
    void testSetAuthoritiesWhitespaceAuthorityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleGrantedAuthority("   "));
    }
}
