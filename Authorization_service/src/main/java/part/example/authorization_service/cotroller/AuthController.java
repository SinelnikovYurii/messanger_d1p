package part.example.authorization_service.cotroller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import part.example.authorization_service.DTO.LoginRequest;
import part.example.authorization_service.DTO.RegisterRequest;
import part.example.authorization_service.JWT.JwtUtil;
import part.example.authorization_service.models.User;
import part.example.authorization_service.repository.UserRepository;
import part.example.authorization_service.service.AuthenticationService;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfo(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            Optional<User> userOptional = userRepository.findByUsername(userDetails.getUsername());
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                return ResponseEntity.ok(Map.of(
                        "userId", user.getId(),
                        "username", user.getUsername()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Пользователь не найден"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("error", "Неверный токен"));
    }

    @GetMapping("/me")
    public ResponseEntity<Long> getCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            Optional<User> user = userRepository.findByUsername(userDetails.getUsername());
            if (user.isPresent()) {
                return ResponseEntity.ok(user.get().getId());
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }


    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return ResponseEntity.ok(Collections.singletonMap("status", "valid"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("error", "Invalid token"));
    }
}
