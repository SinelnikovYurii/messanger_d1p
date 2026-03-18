package part.example.authorization_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import part.example.authorization_service.DTO.LoginRequest;
import part.example.authorization_service.DTO.LoginResult;
import part.example.authorization_service.DTO.RegisterRequest;
import part.example.authorization_service.DTO.RegisterResult;
import part.example.authorization_service.JWT.JwtUtil;
import part.example.authorization_service.exception.BadCredentialsException;
import part.example.authorization_service.exception.InvalidRequestException;
import part.example.authorization_service.exception.UserAlreadyExistsException;
import part.example.authorization_service.models.User;
import part.example.authorization_service.repository.UserRepository;

import java.util.Map;
import java.util.Optional;

/**
 * Сервис аутентификации пользователей.
 * <p>
 * Отвечает за регистрацию и вход. Не знает об HTTP-слое —
 * возвращает доменные объекты и бросает доменные исключения.
 * Контроллер самостоятельно преобразует результаты в {@code ResponseEntity}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    @Value("${core-api.url:http://core-api-service:8082}")
    private String coreApiUrl;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WebClient.Builder webClientBuilder;

    /**
     * Зарегистрировать нового пользователя.
     * <p>
     * Порядок проверок:
     * <ol>
     *   <li>Валидация обязательных полей запроса (до обращения к БД).</li>
     *   <li>Проверка уникальности username.</li>
     *   <li>Сохранение пользователя с хешированным паролем.</li>
     *   <li>Генерация JWT-токена.</li>
     *   <li>Асинхронный вызов core-api-service для инициализации E2EE prekey bundle.</li>
     * </ol>
     *
     * @param request данные для регистрации
     * @return {@link RegisterResult} с токеном и ID созданного пользователя
     * @throws InvalidRequestException    если обязательные поля не заполнены
     * @throws UserAlreadyExistsException если username уже занят
     */
    @Transactional
    public RegisterResult register(RegisterRequest request) {
        // 1. Валидация входных данных — до любых обращений к БД
        validateRegisterRequest(request);

        // 2. Проверка уникальности username
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("[REGISTER] Username '{}' уже занят", request.getUsername());
            throw new UserAlreadyExistsException("Пользователь с таким именем уже существует");
        }

        // 3. Создание и сохранение пользователя
        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName()
        );
        User savedUser = userRepository.save(user);
        log.info("[REGISTER] Пользователь '{}' сохранён с ID={}", savedUser.getUsername(), savedUser.getId());

        // 4. Генерация JWT
        String token = jwtUtil.generateToken(savedUser);

        // 5. Асинхронная инициализация E2EE prekey bundle в core-api-service
        initPreKeyBundleAsync(savedUser.getId(), token);

        return new RegisterResult(token, savedUser.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вход
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Аутентифицировать пользователя по логину и паролю.
     *
     * @param request данные для входа
     * @return {@link LoginResult} с токеном и публичными данными пользователя
     * @throws InvalidRequestException если username или password не переданы
     * @throws BadCredentialsException если пользователь не найден или пароль неверен
     */
    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        // 1. Валидация входных данных — до обращения к БД
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new InvalidRequestException("Имя пользователя обязательно");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidRequestException("Пароль обязателен");
        }

        // 2. Поиск пользователя (единое сообщение — не раскрываем существование аккаунта)
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Неверный логин или пароль"));

        // 3. Проверка пароля
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[LOGIN] Неверный пароль для пользователя '{}'", request.getUsername());
            throw new BadCredentialsException("Неверный логин или пароль");
        }

        log.info("[LOGIN] Пользователь '{}' успешно аутентифицирован", user.getUsername());

        String token = jwtUtil.generateToken(user);

        Map<String, Object> userInfo = Map.of(
                "id",        user.getId(),
                "username",  user.getUsername(),
                "email",     user.getEmail()     != null ? user.getEmail()     : "",
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName",  user.getLastName()  != null ? user.getLastName()  : ""
        );

        return new LoginResult(token, userInfo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Получить публичную информацию о пользователе по его имени.
     *
     * @param username имя пользователя из токена
     * @return {@link Optional} с map полей {@code userId} и {@code username}
     */
    public Optional<Map<String, Object>> getUserInfo(String username) {
        return userRepository.findByUsername(username)
                .map(user -> Map.of(
                        "userId",   (Object) user.getId(),
                        "username", user.getUsername()
                ));
    }

    /**
     * Получить ID пользователя по имени.
     *
     * @param username имя пользователя из токена
     * @return {@link Optional} с ID пользователя
     */
    public Optional<Long> getCurrentUserId(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Приватные методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Валидировать обязательные поля запроса регистрации.
     * Вызывается до любых обращений к БД.
     *
     * @param request запрос регистрации
     * @throws InvalidRequestException если хотя бы одно поле не заполнено
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new InvalidRequestException("Имя пользователя обязательно");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidRequestException("Пароль обязателен");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new InvalidRequestException("Email обязателен");
        }
    }

    /**
     * Асинхронно инициализировать E2EE prekey bundle в core-api-service.
     * <p>
     * Ошибка при вызове не прерывает регистрацию, но фиксируется в логах
     * с уровнем {@code ERROR} для последующей диагностики.
     * Пустые ключи намеренно — core-api-service сгенерирует bundle самостоятельно.
     *
     * @param userId ID зарегистрированного пользователя
     * @param token  JWT-токен для авторизации запроса
     */
    private void initPreKeyBundleAsync(Long userId, String token) {
        String url = coreApiUrl + "/api/users/" + userId + "/prekey-bundle";
        log.info("[REGISTER] Инициализация prekey bundle для userId={}", userId);

        webClientBuilder.build()
                .post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .bodyValue(Map.of(
                        "identityKey",          "",
                        "signedPreKey",         "",
                        "oneTimePreKeys",        "",
                        "signedPreKeySignature", ""
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(resp ->
                        log.info("[REGISTER] Prekey bundle инициализирован для userId={}", userId))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("[REGISTER] core-api-service вернул ошибку {} при инициализации prekey bundle для userId={}: {}",
                                ex.getStatusCode(), userId, ex.getResponseBodyAsString()))
                .doOnError(ex -> !(ex instanceof WebClientResponseException), ex ->
                        log.error("[REGISTER] Не удалось подключиться к core-api-service для userId={}: {}",
                                userId, ex.getMessage()))
                .subscribe();
    }
}
