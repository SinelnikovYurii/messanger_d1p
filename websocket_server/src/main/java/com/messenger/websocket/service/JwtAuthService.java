package websocket.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
public class JwtAuthService {

    private final SecretKey secretKey;

    public JwtAuthService(String jwtSecret) {

        if (jwtSecret == null || jwtSecret.isEmpty()) {
            jwtSecret = "defaultSecretKey12345678901234567890";
        }

        if (jwtSecret.length() < 32) {
            jwtSecret = jwtSecret + "0123456789012345678901234567890123456789";
            jwtSecret = jwtSecret.substring(0, 32);
        }

        log.info("Using JWT secret of length: {}", jwtSecret.length());
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public Long validateTokenAndGetUserId(String token) {
        try {

            if (token == null || token.trim().isEmpty()) {
                log.warn("Token is null or empty");
                return null;
            }


            if (!token.contains(".") || token.split("\\.").length < 2) {
                log.warn("Invalid JWT format: {}", token);
                return null;
            }


            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            log.info("Token validated successfully, subject: {}", subject);

            return 1L;

        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }
}
