package part.example.authorization_service.cotroller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import part.example.authorization_service.DTO.LoginRequest;
import part.example.authorization_service.DTO.LoginResult;
import part.example.authorization_service.DTO.RegisterRequest;
import part.example.authorization_service.DTO.RegisterResult;
import part.example.authorization_service.exception.BadCredentialsException;
import part.example.authorization_service.exception.InvalidRequestException;
import part.example.authorization_service.exception.UserAlreadyExistsException;
import part.example.authorization_service.service.AuthenticationService;

import java.util.Collections;
import java.util.Map;

/**
 * Контроллер аутентификации.
 * <p>
 * Отвечает исключительно за маршрутизацию и преобразование доменных результатов
 * в HTTP-ответы. Вся бизнес-логика делегируется в {@link AuthenticationService}.
 * Доменные исключения перехватываются здесь и преобразуются в соответствующие
 * HTTP-статусы — сервисный слой не знает об HTTP.
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthenticationService authService;

    public AuthController(AuthenticationService authService) {
        this.authService = authService;
    }

    /**
     * Регистрация нового пользователя.
     *
     * @param request данные для регистрации (username, password, email, имя, фамилия)
     * @return 200 с токеном и userId, 400 при невалидных данных или занятом username, 500 при внутренней ошибке
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            RegisterResult result = authService.register(request);
            return ResponseEntity.ok(Map.of(
                    "token",   result.getToken(),
                    "userId",  result.getUserId(),
                    "message", "Пользователь успешно зарегистрирован"
            ));
        } catch (InvalidRequestException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[REGISTER] Внутренняя ошибка при регистрации", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Ошибка при регистрации. Попробуйте позже"));
        }
    }

    /**
     * Вход пользователя по логину и паролю.
     *
     * @param request данные для входа (username, password)
     * @return 200 с токеном и данными пользователя, 400 при невалидных данных, 401 при неверных credentials
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResult result = authService.login(request);
            return ResponseEntity.ok(Map.of(
                    "token",   result.getToken(),
                    "user",    result.getUserInfo(),
                    "message", "Успешная авторизация"
            ));
        } catch (InvalidRequestException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[LOGIN] Внутренняя ошибка при входе", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Ошибка при авторизации. Попробуйте позже"));
        }
    }

    /**
     * Получить публичную информацию о текущем аутентифицированном пользователе.
     *
     * @param authentication контекст аутентификации Spring Security
     * @return {@code { userId, username }} или 401/404
     */
    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfo(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Неверный токен"));
        }
        return authService.getUserInfo(userDetails.getUsername())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Пользователь не найден")));
    }

    /**
     * Получить ID текущего аутентифицированного пользователя.
     *
     * @param authentication контекст аутентификации Spring Security
     * @return ID пользователя или 401
     */
    @GetMapping("/me")
    public ResponseEntity<Long> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return authService.getCurrentUserId(userDetails.getUsername())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * Проверить валидность JWT-токена.
     * Если запрос дошёл до этого метода — токен уже прошёл проверку в фильтре безопасности.
     *
     * @param authentication контекст аутентификации Spring Security
     * @return {@code { status: "valid" }} или 401
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Invalid token"));
        }
        return ResponseEntity.ok(Collections.singletonMap("status", "valid"));
    }
}
