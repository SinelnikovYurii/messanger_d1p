package part.example.authorization_service.service;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import part.example.authorization_service.DTO.AuthResponse;
import part.example.authorization_service.DTO.LoginRequest;
import part.example.authorization_service.DTO.RegisterRequest;
import part.example.authorization_service.DTO.RegistrationResponse;
import part.example.authorization_service.JWT.JwtUtil;
import part.example.authorization_service.models.User;
import part.example.authorization_service.repository.UserRepository;

import java.util.Map;
import java.util.Optional;

@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private WebClient.Builder webClientBuilder;

    public ResponseEntity<?> register(RegisterRequest request) {
        try {
            System.out.println("[REGISTER] Запрос: " + request);
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                System.out.println("[REGISTER] Пользователь с таким именем уже существует: " + request.getUsername());
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Пользователь с таким именем уже существует"));
            }
            if (request.getUsername() == null || request.getUsername().isEmpty() ||
                request.getPassword() == null || request.getPassword().isEmpty() ||
                request.getEmail() == null || request.getEmail().isEmpty()) {
                System.out.println("[REGISTER] Не все обязательные поля заполнены: " + request);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Все обязательные поля должны быть заполнены"));
            }
            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            user.setPassword(encodedPassword);
            User savedUser = userRepository.save(user);
            System.out.println("[REGISTER] Пользователь сохранён: " + savedUser.getId());
            String token = jwtUtil.generateToken(savedUser);
            // --- Вызов core-api-service для генерации prekey bundle ---
            String coreApiUrl = "http://localhost:8083/api/users/" + savedUser.getId() + "/prekey-bundle";
            System.out.println("[REGISTER] Вызов core-api-service: " + coreApiUrl);
            webClientBuilder.build()
                .post()
                .uri(coreApiUrl)
                .header("Authorization", "Bearer " + token)
                .bodyValue(Map.of(
                    "identityKey", "",
                    "signedPreKey", "",
                    "oneTimePreKeys", "",
                    "signedPreKeySignature", ""
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> System.err.println("Ошибка генерации prekey bundle: " + e.getMessage()))
                .doOnSuccess(resp -> System.out.println("[REGISTER] Ответ core-api-service: " + resp))
                .subscribe();
            // --- конец вызова ---
            System.out.println("[REGISTER] Регистрация завершена успешно");
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", savedUser.getId(),
                    "message", "Пользователь успешно зарегистрирован"
            ));
        } catch (Exception e) {
            System.err.println("[REGISTER] Ошибка при регистрации: " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Ошибка при регистрации: " + e.getMessage()));
        }
    }

    public ResponseEntity<?> login(LoginRequest request) {
        try {

            Optional<User> userOptional = userRepository.findByUsername(request.getUsername());

            if (userOptional.isEmpty()) {
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Неверный логин или пароль"));
            }

            User user = userOptional.get();


            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Неверный логин или пароль"));
            }


            String token = jwtUtil.generateToken(user);

            // Создаем объект пользователя для фронтенда (без пароля)
            Map<String, Object> userInfo = Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                    "lastName", user.getLastName() != null ? user.getLastName() : ""
            );

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "user", userInfo,
                    "message", "Успешная авторизация"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Ошибка при авторизации: " + e.getMessage()));
        }
    }
}