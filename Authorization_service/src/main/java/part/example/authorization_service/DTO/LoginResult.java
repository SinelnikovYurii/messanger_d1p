package part.example.authorization_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * Результат успешного входа пользователя.
 * Возвращается из сервисного слоя — не содержит HTTP-деталей.
 */
@Getter
@AllArgsConstructor
public class LoginResult {
    /** JWT-токен аутентификации. */
    private final String token;
    /** Публичные данные пользователя (без пароля). */
    private final Map<String, Object> userInfo;
}
