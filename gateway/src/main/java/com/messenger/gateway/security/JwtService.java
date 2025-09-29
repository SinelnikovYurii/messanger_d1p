package com.messenger.gateway.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            logger.error("JWT token malformed: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            logger.error("JWT signature validation failed: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            logger.error("JWT token compact of handler are invalid: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isTokenValid(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            boolean isValid = (extractedUsername.equals(username)) && !isTokenExpired(token);
            logger.debug("Token validation for user {}: {}", username, isValid);
            return isValid;
        } catch (Exception e) {
            logger.warn("Token validation failed for user {}: {}", username, e.getMessage());
            return false;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            boolean isExpired = isTokenExpired(token);
            String username = claims.getSubject();
            Long userId = claims.get("id", Long.class);

            logger.debug("Token validation - User: {}, UserId: {}, Expired: {}", username, userId, isExpired);

            if (isExpired) {
                logger.warn("Token expired for user: {}", username);
                return false;
            }

            logger.debug("Token is valid for user: {}", username);
            return true;
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            Date now = new Date();
            boolean expired = expiration.before(now);

            if (expired) {
                logger.debug("Token expired at: {}, current time: {}", expiration, now);
            }

            return expired;
        } catch (Exception e) {
            logger.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String generateToken(String username, Long userId) {
        Date now = new Date(System.currentTimeMillis());
        Date expiration = new Date(System.currentTimeMillis() + jwtExpiration);

        logger.debug("Generating token for user: {} (ID: {}), expires: {}", username, userId, expiration);

        return Jwts.builder()
                .setSubject(username)
                .claim("id", userId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public Long extractUserId(String token) {
        try {
            Long userId = extractClaim(token, claims -> claims.get("id", Long.class));
            logger.debug("Extracted user ID from token: {}", userId);
            return userId;
        } catch (Exception e) {
            logger.error("Error extracting user ID from token: {}", e.getMessage());
            throw e;
        }
    }
}
