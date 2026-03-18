package com.messenger.core.service.user;

import com.messenger.core.config.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Реализация {@link UserContextResolver}.
 * Сначала пытается получить ID из заголовка X-User-Id (проставляется Gateway),
 * затем — через JWT-токен из Authorization-заголовка.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserContextResolverImpl implements UserContextResolver {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Override
    public Long resolveUserId(HttpServletRequest request) {
        // 1. Заголовок от Gateway
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Неверный формат ID пользователя в заголовке: " + userIdHeader);
            }
        }

        // 2. JWT-токен
        Long userId;
        try {
            userId = jwtAuthenticationFilter.getUserIdFromRequest(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ошибка авторизации: " + e.getMessage(), e);
        }

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Пользователь не аутентифицирован - отсутствуют данные авторизации");
        }

        log.debug("Resolved userId={} via JWT", userId);
        return userId;
    }

    @Override
    public Long resolveUserId(HttpServletRequest request, String tokenParam) {
        // 1. Заголовок от Gateway — наивысший приоритет
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Неверный формат ID пользователя в заголовке: " + userIdHeader);
            }
        }

        // 2. JWT из query-параметра (для GET-запросов ресурсов)
        if (tokenParam != null && !tokenParam.isEmpty()) {
            try {
                Long userId = jwtAuthenticationFilter.getUserIdFromToken(tokenParam);
                if (userId != null) {
                    log.debug("Resolved userId={} via query token param", userId);
                    return userId;
                }
            } catch (Exception e) {
                log.warn("Failed to resolve userId from query token param: {}", e.getMessage());
            }
        }

        // 3. JWT из Authorization-заголовка
        return resolveUserId(request);
    }
}
