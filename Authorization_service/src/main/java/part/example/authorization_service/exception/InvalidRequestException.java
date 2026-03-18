package part.example.authorization_service.exception;

/**
 * Выбрасывается при передаче невалидных или неполных данных запроса.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
