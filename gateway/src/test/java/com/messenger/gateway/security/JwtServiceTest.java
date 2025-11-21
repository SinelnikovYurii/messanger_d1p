package com.messenger.gateway.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;
    private long expiration;
    private SecretKey key;
    private String validToken;
    private String username;
    private Long userId;
    private String secret;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        secret = "supersecretkeysupersecretkeysupersecretkeysupersecretkeysupersecretkeysupersecretkey12";
        expiration = 1000000L;
        username = "testuser";
        userId = 42L;
        // Установка приватных полей через рефлексию
        try {
            var f1 = JwtService.class.getDeclaredField("secretKey");
            f1.setAccessible(true);
            f1.set(jwtService, secret);
            var f2 = JwtService.class.getDeclaredField("jwtExpiration");
            f2.setAccessible(true);
            f2.set(jwtService, expiration);
        } catch (Exception e) { throw new RuntimeException(e); }
        key = Keys.hmacShaKeyFor(secret.getBytes());
        validToken = jwtService.generateToken(username, userId);
    }

    @Test
    void testGenerateAndExtractUsername() {
        assertEquals(username, jwtService.extractUsername(validToken));
    }

    @Test
    void testExtractUserId() {
        assertEquals(userId, jwtService.extractUserId(validToken));
    }

    @Test
    void testIsTokenValid_Valid() {
        assertTrue(jwtService.isTokenValid(validToken, username));
        assertTrue(jwtService.isTokenValid(validToken));
    }

    @Test
    void testIsTokenValid_InvalidUsername() {
        assertFalse(jwtService.isTokenValid(validToken, "otheruser"));
    }

    @Test
    void testIsTokenValid_Expired() {
        Date now = new Date(System.currentTimeMillis() - expiration - 1000);
        Date exp = new Date(System.currentTimeMillis() - 1000);
        String expiredToken = Jwts.builder()
                .setSubject(username)
                .claim("id", userId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key)
                .compact();
        assertFalse(jwtService.isTokenValid(expiredToken, username));
        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    void testIsTokenValid_Malformed() {
        assertFalse(jwtService.isTokenValid("not.a.jwt.token", username));
        assertFalse(jwtService.isTokenValid("not.a.jwt.token"));
    }

    @Test
    void testIsTokenValid_WrongKey() {
        String otherSecret = "anothersecretkeyanothersecretkeyanothersecretkeyanothersecretkeyanothersecretkeyanothersecretkey12";
        SecretKey otherKey = Keys.hmacShaKeyFor(otherSecret.getBytes());
        String otherToken = Jwts.builder()
                .setSubject(username)
                .claim("id", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(otherKey)
                .compact();
        assertFalse(jwtService.isTokenValid(otherToken, username));
        assertFalse(jwtService.isTokenValid(otherToken));
    }

    @Test
    void testExtractClaim_InvalidToken() {
        assertThrows(Exception.class, () -> jwtService.extractClaim("invalid.token", Claims::getSubject));
    }

    @Test
    void testExtractAllClaims_ExpiredJwtException() {
        Date now = new Date(System.currentTimeMillis() - expiration - 1000);
        Date exp = new Date(System.currentTimeMillis() - 1000);
        String expiredToken = Jwts.builder()
                .setSubject(username)
                .claim("id", userId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key)
                .compact();
        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(expiredToken));
    }

    @Test
    void testExtractAllClaims_UnsupportedJwtException() {
        String unsupportedToken = validToken + "extra";
        assertThrows(Exception.class, () -> jwtService.extractUsername(unsupportedToken));
    }

    @Test
    void testExtractAllClaims_SignatureException() {
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";
        assertThrows(Exception.class, () -> jwtService.extractUsername(tamperedToken));
    }

    @Test
    void testIsTokenExpired_Boundary() {
        Date now = new Date();
        String boundaryToken = Jwts.builder()
                .setSubject(username)
                .claim("id", userId)
                .setIssuedAt(now)
                .setExpiration(now)
                .signWith(key)
                .compact();
        assertFalse(jwtService.isTokenValid(boundaryToken, username));
        assertFalse(jwtService.isTokenValid(boundaryToken));
    }

    @Test
    void testExtractUserId_InvalidToken() {
        assertThrows(Exception.class, () -> jwtService.extractUserId("invalid.token"));
    }

    @Test
    void testExpiredToken() {
        String expiredToken = Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();
        assertFalse(jwtService.isTokenValid(expiredToken));
        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(expiredToken));
    }

    @Test
    void testMalformedToken() {
        String malformedToken = "not.a.jwt.token";
        assertFalse(jwtService.isTokenValid(malformedToken));
        assertThrows(MalformedJwtException.class, () -> jwtService.extractUsername(malformedToken));
    }

    @Test
    void testIsTokenExpired() {
        String expiredToken = Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();
        assertTrue(jwtService.isTokenExpired(expiredToken));
        assertFalse(jwtService.isTokenExpired(validToken));
    }

    @Test
    void testExtractClaim() {
        String claim = jwtService.extractClaim(validToken, Claims::getSubject);
        assertEquals(username, claim);
    }

    @Test
    void testShortSecretKey() {
        JwtService service = new JwtService();
        String shortSecret = "short";
        try {
            var f1 = JwtService.class.getDeclaredField("secretKey");
            f1.setAccessible(true);
            f1.set(service, shortSecret);
            var f2 = JwtService.class.getDeclaredField("jwtExpiration");
            f2.setAccessible(true);
            f2.set(service, expiration);
        } catch (Exception e) { throw new RuntimeException(e); }
        assertThrows(Exception.class, () -> service.generateToken(username, userId));
    }

    @Test
    void testLongSecretKey() {
        JwtService service = new JwtService();
        String longSecret = "a".repeat(512);
        try {
            var f1 = JwtService.class.getDeclaredField("secretKey");
            f1.setAccessible(true);
            f1.set(service, longSecret);
            var f2 = JwtService.class.getDeclaredField("jwtExpiration");
            f2.setAccessible(true);
            f2.set(service, expiration);
        } catch (Exception e) { throw new RuntimeException(e); }
        String token = service.generateToken(username, userId);
        assertNotNull(token);
    }

    @Test
    void testTokenWithNestedPayload() {
        String nestedUsername = "{\"user\":{\"name\":\"test\"}}";
        String token = Jwts.builder()
                .setSubject(nestedUsername)
                .claim("id", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
        assertEquals(nestedUsername, jwtService.extractUsername(token));
    }

    @Test
    void testSqlInjectionInUsername() {
        String sqlUsername = "test' OR '1'='1";
        String token = jwtService.generateToken(sqlUsername, userId);
        assertEquals(sqlUsername, jwtService.extractUsername(token));
    }

    @Test
    void testXssInUsername() {
        String xssUsername = "<script>alert('xss')</script>";
        String token = jwtService.generateToken(xssUsername, userId);
        assertEquals(xssUsername, jwtService.extractUsername(token));
    }
}
