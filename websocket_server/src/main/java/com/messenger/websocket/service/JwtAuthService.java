package websocket.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Slf4j
@Service
public class JwtAuthService {

    private final String secretKey;

    public JwtAuthService(@Value("${jwt.secret}") String secret) {
        this.secretKey = secret;
        log.info("🔧 [JWT] JwtAuthService initialized with secret key length: {}", secret.length());
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public boolean validateToken(String token) {
        try {
            log.info("🔑 [JWT] Validating token: {}...", token.substring(0, Math.min(token.length(), 20)));
            log.info("🔑 [JWT] Using secret key length: {}", secretKey.length());

            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);

            log.info("✅ [JWT] Token validation successful");
            return true;
        } catch (Exception e) {
            log.error("❌ [JWT] Invalid JWT token: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            log.info("🔑 [JWT] Extracting username from token");
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

            String username = claims.getSubject();
            log.info("✅ [JWT] Extracted username: {}", username);
            return username;
        } catch (Exception e) {
            log.error("❌ [JWT] Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            log.info("🔑 [JWT] Extracting user ID from token");
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

            // Пытаемся получить userId из claims разными способами
            Object idClaim = claims.get("id");
            Long userId = null;

            if (idClaim instanceof Number) {
                userId = ((Number) idClaim).longValue();
            } else if (idClaim instanceof String) {
                userId = Long.valueOf((String) idClaim);
            }

            log.info("✅ [JWT] Extracted user ID: {}", userId);
            return userId;
        } catch (Exception e) {
            log.error("❌ [JWT] Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }
}
