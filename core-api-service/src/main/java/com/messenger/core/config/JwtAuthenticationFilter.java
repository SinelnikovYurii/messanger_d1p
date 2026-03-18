package com.messenger.core.config;


import com.messenger.core.service.user.UserService;
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
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT-фильтр аутентификации для входящих HTTP-запросов.
 * <p>
 * Выполняется один раз на каждый запрос ({@link OncePerRequestFilter}).
 * Логика обработки:
 * <ol>
 *   <li>Если присутствуют заголовки {@code X-Internal-Service} и {@code X-Service-Auth}
 *       с корректными значениями — запрос помечается как внутренний сервисный
 *       и получает роль {@code ROLE_INTERNAL_SERVICE} без проверки JWT.</li>
 *   <li>В остальных случаях извлекается JWT из заголовка {@code Authorization},
 *       валидируется подпись и срок действия, после чего пользователь получает
 *       роль {@code ROLE_USER} и помещается в {@link SecurityContextHolder}.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final UserService userService;

    /**
     * Основной метод фильтрации. Определяет тип запроса (внутренний сервис / внешний клиент)
     * и устанавливает соответствующий контекст аутентификации Spring Security.
     *
     * @param request     входящий HTTP-запрос
     * @param response    исходящий HTTP-ответ
     * @param filterChain цепочка фильтров для передачи запроса дальше
     * @throws ServletException при ошибке обработки сервлета
     * @throws IOException      при ошибке ввода-вывода
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String internalService = request.getHeader("X-Internal-Service");
        String serviceAuth     = request.getHeader("X-Service-Auth");

        if ("websocket-server".equals(internalService) && "internal-service-key".equals(serviceAuth)) {
            log.debug("Внутренний сервисный запрос от '{}' — JWT-проверка пропущена", internalService);

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken("internal-service", null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
            return;
        }

        String token = getTokenFromRequest(request);

        if (token != null && validateToken(token)) {
            String username = getUsernameFromToken(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_USER"));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Аутентификация установлена для пользователя '{}'", username);
            }
        } else {
            log.warn("JWT-токен отсутствует или недействителен — запрос {} {} не аутентифицирован",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Извлечь JWT-токен из заголовка {@code Authorization}.
     * Ожидается формат: {@code Bearer <token>}.
     * Токен дополнительно проверяется на допустимые символы Base64URL.
     *
     * @param request входящий HTTP-запрос
     * @return строка токена без префикса {@code Bearer }, или {@code null} если токен отсутствует
     *         или содержит недопустимые символы
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            if (token.matches("[A-Za-z0-9+/=._-]+")) {
                return token;
            }
            log.warn("JWT-токен содержит недопустимые символы, запрос отклонён");
        }
        return null;
    }

    /**
     * Извлечь имя пользователя (subject) из JWT-токена.
     *
     * @param token строка JWT-токена
     * @return имя пользователя, или {@code null} если токен невалиден
     */
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
            log.error("Ошибка извлечения имени пользователя из токена: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Извлечь имя пользователя из JWT-токена, содержащегося в HTTP-запросе.
     *
     * @param request входящий HTTP-запрос
     * @return имя пользователя, или {@code null} если токен отсутствует или невалиден
     */
    public String getUsernameFromRequest(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        return token != null ? getUsernameFromToken(token) : null;
    }

    /**
     * Получить ID пользователя из JWT-токена, содержащегося в HTTP-запросе.
     * Выполняет поиск пользователя в БД по извлечённому имени.
     *
     * @param request входящий HTTP-запрос
     * @return ID пользователя, или {@code null} если токен отсутствует или пользователь не найден
     */
    public Long getUserIdFromRequest(HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username != null) {
            return userService.findByUsername(username)
                    .map(com.messenger.core.model.User::getId)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Получить ID пользователя непосредственно из строки JWT-токена.
     * Выполняет поиск пользователя в БД по извлечённому имени.
     *
     * @param token строка JWT-токена
     * @return ID пользователя, или {@code null} если токен невалиден или пользователь не найден
     */
    public Long getUserIdFromToken(String token) {
        String username = getUsernameFromToken(token);
        if (username != null) {
            return userService.findByUsername(username)
                    .map(com.messenger.core.model.User::getId)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Проверить валидность JWT-токена: формат, подпись и срок действия.
     *
     * @param token строка JWT-токена
     * @return {@code true} если токен валиден, {@code false} в противном случае
     */
    public boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                log.debug("Токен пустой или null");
                return false;
            }

            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.error("Неверный формат JWT: ожидается 3 части, получено {}", parts.length);
                return false;
            }

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("Недействительная подпись JWT (возможно, другой секретный ключ): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Ошибка валидации JWT-токена: {}", e.getMessage());
            return false;
        }
    }
}
