package part.example.authorization_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Результат успешной регистрации пользователя.
 * Возвращается из сервисного слоя — не содержит HTTP-деталей.
 */
@Getter
@AllArgsConstructor
public class RegisterResult {
    /** JWT-токен для немедленной аутентификации после регистрации. */
    private final String token;
    /** ID созданного пользователя. */
    private final Long userId;
}
