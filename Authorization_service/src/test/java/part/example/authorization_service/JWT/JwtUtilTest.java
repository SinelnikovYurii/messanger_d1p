package part.example.authorization_service.JWT;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private JwtUtil jwtUtil;
    private String token;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        jwtUtil.setSecretString("supersecretkeysupersecretkeysupersecretkeysupersecretkeysupersecretkeysupersecretkey12");
        jwtUtil.init();
        part.example.authorization_service.models.User testUser = new part.example.authorization_service.models.User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        token = jwtUtil.generateToken(testUser);
    }

    @Test
    void testGenerateAndValidateToken_Valid() {
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testValidateToken_Invalid() {
        assertFalse(jwtUtil.validateToken(token + "broken"));
    }

    @Test
    void testExtractUsername() {
        String username = jwtUtil.extractUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    void testValidateTokenWithUserDetails_Valid() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        assertTrue(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void testValidateTokenWithUserDetails_InvalidUsername() {
        UserDetails userDetails = new User("otheruser", "password", Collections.emptyList());
        assertFalse(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void testValidateTokenWithUserDetails_Expired() {
        // Сгенерировать токен с истёкшим сроком
        String expiredToken = Jwts.builder()
                .setSubject("testuser")
                .claim("id", 1L)
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000000))
                .setExpiration(new Date(System.currentTimeMillis() - 500000))
                .signWith(jwtUtil.getSecretKey(), SignatureAlgorithm.HS512)
                .compact();
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        assertFalse(jwtUtil.validateToken(expiredToken, userDetails));
    }

    @Test
    void testValidateToken_NullToken() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    void testValidateToken_EmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void testValidateToken_MalformedToken() {
        assertFalse(jwtUtil.validateToken("not.a.jwt.token"));
    }

    @Test
    void testValidateToken_WrongKey() {
        JwtUtil otherUtil = new JwtUtil();
        otherUtil.setSecretString("anothersecretkeyanothersecretkeyanothersecretkeyanothersecretkeyanothersecretkeyanothersecretkey12");
        otherUtil.init();
        part.example.authorization_service.models.User otherUser = new part.example.authorization_service.models.User();
        otherUser.setId(2L);
        otherUser.setUsername("otheruser");
        String otherToken = otherUtil.generateToken(otherUser);
        assertFalse(jwtUtil.validateToken(otherToken));
    }

    @Test
    void testExtractAllClaims_InvalidToken() {
        assertThrows(Exception.class, () -> jwtUtil.extractAllClaims("invalid.token.value"));
    }

    @Test
    void testGenerateTokenWithCustomUser() {
        part.example.authorization_service.models.User customUser = new part.example.authorization_service.models.User();
        customUser.setId(999L);
        customUser.setUsername("custom_user");
        String customToken = jwtUtil.generateToken(customUser);
        assertEquals("custom_user", jwtUtil.extractUsername(customToken));
    }

    @Test
    void testValidateTokenWithUserDetails_ExpiresNow() {
        String nowToken = Jwts.builder()
                .setSubject("testuser")
                .claim("id", 1L)
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000))
                .setExpiration(new Date(System.currentTimeMillis()))
                .signWith(jwtUtil.getSecretKey(), SignatureAlgorithm.HS512)
                .compact();
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        assertFalse(jwtUtil.validateToken(nowToken, userDetails));
    }
}
