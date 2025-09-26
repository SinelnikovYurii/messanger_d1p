package com.messenger.core.config;

import com.messenger.core.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Получаем токен из заголовка или параметра запроса
        String token = getTokenFromRequest(request);

        if (token != null && jwtService.isTokenValid(token)) {
            try {
                String username = jwtService.extractUsername(token);
                Long userId = jwtService.extractUserId(token);

                // Создаем аутентификацию
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                // Устанавливаем в контекст безопасности
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Добавляем userId в атрибуты запроса для использования в контроллерах
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);

            } catch (Exception e) {
                // Токен невалидный, очищаем контекст
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        // Сначала пробуем получить из заголовка Authorization
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // Потом из заголовков, переданных через Gateway
        String tokenFromGateway = request.getHeader("X-JWT-Token");
        if (tokenFromGateway != null) {
            return tokenFromGateway;
        }

        // Наконец, из параметра запроса (для WebSocket)
        return request.getParameter("token");
    }
}
