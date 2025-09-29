package com.messenger.core.config;

import com.messenger.core.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.info("=== Core API JWT Filter Debug ===");
        log.info("Processing request: {} {}", request.getMethod(), request.getRequestURI());
        log.info("Authorization header: {}", request.getHeader("Authorization"));
        log.info("X-Gateway-Request header: {}", request.getHeader("X-Gateway-Request"));
        log.info("All headers: ");
        request.getHeaderNames().asIterator().forEachRemaining(headerName ->
            log.info("  {}: {}", headerName, request.getHeader(headerName)));

        String token = getTokenFromRequest(request);
        log.info("Extracted token: {}", token != null ? "***" + token.substring(Math.max(0, token.length() - 10)) : "null");

        if (token != null && validateToken(token)) {
            String username = getUsernameFromToken(token);
            log.info("Extracted username from token: {}", username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Добавляем роль ROLE_USER
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                // Создаем аутентификацию с ролями
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Set authentication for user: {} with roles: {}", username, authorities);
            }
        } else {
            log.warn("No valid token found - request will be unauthorized");
        }
        log.info("=================================");

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        log.debug("Authorization header: {}", bearerToken != null ? "Bearer ***" : "null");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            // Проверяем, что токен соответствует формату JWT (Base64URL кодирование)
            // JWT использует символы: A-Z, a-z, 0-9, +, /, =, -, _
            if (token.matches("[A-Za-z0-9+/=._-]+")) {
                return token;
            } else {
                log.error("Token contains invalid characters: {}", token);
                return null;
            }
        }
        return null;
    }

    public String getUsernameFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    public String getUsernameFromRequest(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        if (token != null) {
            return getUsernameFromToken(token);
        }
        return null;
    }

    public Long getUserIdFromRequest(HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username != null) {
            return userService.findByUsername(username)
                    .map(user -> user.getId())
                    .orElse(null);
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                log.debug("Token is null or empty");
                return false;
            }

            // Проверяем базовый формат JWT (3 части разделенные точками)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid JWT format - expected 3 parts, got {}", parts.length);
                return false;
            }

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            log.debug("Token validation successful");
            return true;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("Invalid JWT signature - token was likely created with different secret key: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}
