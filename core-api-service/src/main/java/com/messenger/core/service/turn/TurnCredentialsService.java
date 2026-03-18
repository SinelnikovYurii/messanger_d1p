package com.messenger.core.service.turn;

import java.util.Map;

/**
 * Абстракция для генерации временных TURN-credentials по алгоритму HMAC-SHA1 (RFC 8489).
 * Отделяет бизнес-логику от HTTP-слоя (SRP, DIP).
 */
public interface TurnCredentialsService {

    /**
     * Формирует набор TURN-credentials для указанного пользователя.
     *
     * @param username имя (логин) пользователя
     * @return map с полями urls, username, credential, ttl — готов для JSON-ответа
     */
    Map<String, Object> generateCredentials(String username);
}
