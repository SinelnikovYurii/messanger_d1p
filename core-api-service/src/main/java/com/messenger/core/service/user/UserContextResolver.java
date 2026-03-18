package com.messenger.core.service.user;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Абстракция для извлечения идентификатора текущего пользователя из HTTP-запроса.
 * Поддерживает два источника: заголовок X-User-Id (Gateway) и JWT-токен.
 */
public interface UserContextResolver {

    /**
     * Извлекает ID текущего пользователя из запроса.
     *
     * @param request входящий HTTP-запрос
     * @return ID пользователя
     * @throws IllegalArgumentException если пользователь не аутентифицирован
     *                                  или заголовок содержит некорректный формат
     */
    Long resolveUserId(HttpServletRequest request);

    /**
     * Извлекает ID текущего пользователя из запроса с возможностью передать
     * JWT-токен через query-параметр (актуально для GET-запросов ресурсов,
     * например, изображений).
     *
     * @param request    входящий HTTP-запрос
     * @param tokenParam JWT-токен из query-параметра (может быть null)
     * @return ID пользователя
     * @throws IllegalArgumentException если пользователь не аутентифицирован
     */
    Long resolveUserId(HttpServletRequest request, String tokenParam);
}
