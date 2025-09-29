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

    public ResponseEntity<?> register(RegisterRequest request) {
        try {

            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Пользователь с таким именем уже существует"));
            }


            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());


            String encodedPassword = passwordEncoder.encode(request.getPassword());
            user.setPassword(encodedPassword);

            User savedUser = userRepository.save(user);


            String token = jwtUtil.generateToken(savedUser);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", savedUser.getId(),
                    "message", "Пользователь успешно зарегистрирован"
            ));
        } catch (Exception e) {
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