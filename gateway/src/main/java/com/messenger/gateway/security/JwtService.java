package com.messenger.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    @Value("${security.jwt.secret}")
    private String secretKey;

    @Value("${security.jwt.expiration}")
    private long jwtExpiration;

    private SecretKey signingKey;

    // Ленивая инициализация ключа подписи
    private SecretKey getSigningKey() {
        if (signingKey == null) {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes) long");
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }

    public Mono<String> extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Mono<Long> extractUserId(String token) {
        return extractClaim(token, claims -> {
            Object userIdClaim = claims.get("userId");
            if (userIdClaim instanceof Number) {
                return ((Number) userIdClaim).longValue();
            }
            if (userIdClaim instanceof String) {
                try {
                    return Long.parseLong((String) userIdClaim);
                } catch (NumberFormatException e) {
                    log.error("Cannot parse userId from token: {}", userIdClaim);
                    return null;
                }
            }
            return null;
        });
    }

    public Mono<Boolean> isTokenValid(String token) {
        return isTokenExpired(token)
                .map(expired -> !expired)
                .onErrorReturn(false);
    }

    public Mono<Boolean> isTokenExpired(String token) {
        return extractExpiration(token)
                .map(expiration -> expiration.before(new Date()))
                .onErrorReturn(true);
    }

    public Mono<Date> extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> Mono<T> extractClaim(String token, Function<Claims, T> claimsResolver) {
        return extractAllClaims(token)
                .map(claimsResolver)
                .onErrorReturn(null);
    }

    private Mono<Claims> extractAllClaims(String token) {
        return Mono.fromCallable(() -> {
            try {
                // Исправлено для JJWT 0.12.x: parserBuilder() заменен на parser()
                return Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException e) {
                log.error("JWT parsing failed: {}", e.getMessage());
                throw new RuntimeException("Invalid JWT token", e);
            } catch (Exception e) {
                log.error("Unexpected error during JWT parsing: {}", e.getMessage());
                throw new RuntimeException("JWT processing error", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Метод для создания токена (если понадобится)
    public String generateToken(String username, Long userId) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    // Синхронные методы для обратной совместимости
    public String extractUsernameSync(String token) {
        try {
            final Claims claims = parseTokenSync(token);
            return claims != null ? claims.getSubject() : null;
        } catch (Exception e) {
            log.error("Error extracting username: {}", e.getMessage());
            return null;
        }
    }

    public Long extractUserIdSync(String token) {
        try {
            final Claims claims = parseTokenSync(token);
            if (claims == null) return null;

            Object userIdClaim = claims.get("userId");
            if (userIdClaim instanceof Number) {
                return ((Number) userIdClaim).longValue();
            }
            if (userIdClaim instanceof String) {
                return Long.parseLong((String) userIdClaim);
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting userId: {}", e.getMessage());
            return null;
        }
    }

    public boolean isTokenValidSync(String token) {
        try {
            final Claims claims = parseTokenSync(token);
            return claims != null && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseTokenSync(String token) {
        try {
            // Исправлено для JJWT 0.12.x: parserBuilder() заменен на parser()
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.error("JWT parsing failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during JWT parsing: {}", e.getMessage());
            return null;
        }
    }
}
