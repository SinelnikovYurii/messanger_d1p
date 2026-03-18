package part.example.authorization_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ответ после успешной регистрации пользователя.
 */
@Getter
@AllArgsConstructor
public class RegistrationResponse {
    private final String message;
}
