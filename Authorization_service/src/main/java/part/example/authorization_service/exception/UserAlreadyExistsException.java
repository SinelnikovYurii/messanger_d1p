package part.example.authorization_service.exception;

/**
 * Выбрасывается при попытке зарегистрировать пользователя
 * с именем или email, которые уже заняты.
 */
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
