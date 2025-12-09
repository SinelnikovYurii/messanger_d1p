package com.messenger.websocket.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.messenger.websocket.service.JwtAuthService;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JwtAuthServiceTest {

    private JwtAuthService jwtAuthService;
    private String secretKey;

    @BeforeEach
    void setUp() {
        secretKey = "mySecretKeyForJWTTokenGenerationAndValidation12345678901234567890";
        jwtAuthService = new JwtAuthService(secretKey);
    }

    private String generateValidToken(String username, Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userId);

        return Jwts.builder()
            .setSubject(username)
            .addClaims(claims)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 864000000)) // 10 days
            .signWith(key)
            .compact();
    }

    @Test
    void testValidateToken_valid() {
        String token = generateValidToken("testuser", 1L);
        assertTrue(jwtAuthService.validateToken(token));
    }

    @Test
    void testValidateToken_invalid() {
        String invalidToken = "invalid.token.here";
        assertFalse(jwtAuthService.validateToken(invalidToken));
    }

    @Test
    void testValidateToken_expired() {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
        String expiredToken = Jwts.builder()
            .setSubject("testuser")
            .setIssuedAt(new Date(System.currentTimeMillis() - 20000))
            .setExpiration(new Date(System.currentTimeMillis() - 10000)) // Already expired
            .signWith(key)
            .compact();

        assertFalse(jwtAuthService.validateToken(expiredToken));
    }

    @Test
    void testGetUsernameFromToken() {
        String username = "testuser";
        String token = generateValidToken(username, 1L);

        String extractedUsername = jwtAuthService.getUsernameFromToken(token);
        assertEquals(username, extractedUsername);
    }

    @Test
    void testGetUsernameFromToken_invalid() {
        String invalidToken = "invalid.token.here";
        String username = jwtAuthService.getUsernameFromToken(invalidToken);
        assertNull(username);
    }

    @Test
    void testGetUserIdFromToken() {
        Long userId = 123L;
        String token = generateValidToken("testuser", userId);

        Long extractedUserId = jwtAuthService.getUserIdFromToken(token);
        assertEquals(userId, extractedUserId);
    }

    @Test
    void testGetUserIdFromToken_invalid() {
        String invalidToken = "invalid.token.here";
        Long userId = jwtAuthService.getUserIdFromToken(invalidToken);
        assertNull(userId);
    }

    @Test
    void testGetUserIdFromToken_stringId() {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", "456"); // String вместо Long

        String token = Jwts.builder()
            .setSubject("testuser")
            .addClaims(claims)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 864000000))
            .signWith(key)
            .compact();

        Long userId = jwtAuthService.getUserIdFromToken(token);
        assertEquals(456L, userId);
    }

    @Test
    void testTokenWithoutUserId() {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
        String token = Jwts.builder()
            .setSubject("testuser")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 864000000))
            .signWith(key)
            .compact();

        Long userId = jwtAuthService.getUserIdFromToken(token);
        assertNull(userId);
    }

    @Test
    void testValidateToken_emptyToken() {
        assertFalse(jwtAuthService.validateToken(""));
    }

    @Test
    void testValidateToken_nullToken() {
        // validateToken обрабатывает null токен и возвращает false вместо выброса исключения
        assertFalse(jwtAuthService.validateToken(null));
    }
}

