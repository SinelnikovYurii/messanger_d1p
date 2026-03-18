package part.example.authorization_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ответ с JWT-токеном после успешной аутентификации.
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private final String token;
}