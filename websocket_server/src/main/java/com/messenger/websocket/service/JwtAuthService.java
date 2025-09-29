package websocket.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class JwtAuthService {

    private final String secretKey;

    public JwtAuthService(String secret) {
        this.secretKey = secret;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.debug("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
            // Пытаемся получить userId из claims разными способами
            Object userIdObj = claims.get("userId");
            if (userIdObj != null) {
                if (userIdObj instanceof Number) {
                    return ((Number) userIdObj).longValue();
                } else if (userIdObj instanceof String) {
                    return Long.valueOf((String) userIdObj);
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }
}
