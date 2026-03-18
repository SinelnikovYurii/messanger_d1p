package part.example.authorization_service.exception;

/**
 * Выбрасывается при неверных учётных данных при входе.
 */
public class BadCredentialsException extends RuntimeException {
    public BadCredentialsException(String message) {
        super(message);
    }
}
