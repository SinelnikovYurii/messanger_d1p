package part.example.authorization_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import part.example.authorization_service.DTO.LoginRequest;
import part.example.authorization_service.DTO.RegisterRequest;
import part.example.authorization_service.JWT.JwtUtil;
import part.example.authorization_service.models.User;
import part.example.authorization_service.repository.UserRepository;

import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        Mockito.when(userRepository.findByUsername(eq("newuser"))).thenReturn(Optional.empty());
        Mockito.when(passwordEncoder.encode(any())).thenReturn("encodedPassword");
        Mockito.when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        ResponseEntity<?> response = authenticationService.register(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).containsKey("token"));
    }

    @Test
    void register_userExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        Mockito.when(userRepository.findByUsername(eq("existing"))).thenReturn(Optional.of(Mockito.mock(User.class)));
        ResponseEntity<?> response = authenticationService.register(request);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");
        request.setPassword("pass");

        User user = new User();
        user.setUsername("user");
        user.setPassword("encoded");
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        Mockito.when(userRepository.findByUsername(eq("user"))).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches(eq("pass"), eq("encoded"))).thenReturn(true);
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        ResponseEntity<?> response = authenticationService.login(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).containsKey("token"));
    }

    @Test
    void login_wrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");
        request.setPassword("wrong");
        User user = new User();
        user.setUsername("user");
        user.setPassword("encoded");
        Mockito.when(userRepository.findByUsername(eq("user"))).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches(eq("wrong"), eq("encoded"))).thenReturn(false);
        ResponseEntity<?> response = authenticationService.login(request);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void login_userNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nouser");
        Mockito.when(userRepository.findByUsername(eq("nouser"))).thenReturn(Optional.empty());
        ResponseEntity<?> response = authenticationService.login(request);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void register_repositoryException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        Mockito.when(userRepository.findByUsername(eq("newuser"))).thenThrow(new RuntimeException("DB error"));
        ResponseEntity<?> response = authenticationService.register(request);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).get("error").toString().contains("Ошибка при регистрации"));
    }

    @Test
    void login_repositoryException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");
        Mockito.when(userRepository.findByUsername(eq("user"))).thenThrow(new RuntimeException("DB error"));
        ResponseEntity<?> response = authenticationService.login(request);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).get("error").toString().contains("Ошибка при авторизации"));
    }

    @Test
    void register_emptyPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("");
        Mockito.when(userRepository.findByUsername(eq("newuser"))).thenReturn(Optional.empty());
        Mockito.when(passwordEncoder.encode(any())).thenReturn("");
        Mockito.when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        ResponseEntity<?> response = authenticationService.register(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).containsKey("token"));
    }

    @Test
    void login_emptyPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");
        request.setPassword("");
        User user = new User();
        user.setUsername("user");
        user.setPassword(""); // Явно указываем пустую строку, чтобы избежать null
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        Mockito.when(userRepository.findByUsername(eq("user"))).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches(eq(""), eq(""))).thenReturn(true);
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        ResponseEntity<?> response = authenticationService.login(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).containsKey("token"));
    }

    @Test
    void register_emptyUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        Mockito.when(userRepository.findByUsername(eq(""))).thenReturn(Optional.empty());
        Mockito.when(passwordEncoder.encode(any())).thenReturn("encodedPassword");
        Mockito.when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        ResponseEntity<?> response = authenticationService.register(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(((Map<?,?>)response.getBody()).containsKey("token"));
    }

    @Test
    void login_emptyUsername() {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("pass");
        Mockito.when(userRepository.findByUsername(eq(""))).thenReturn(Optional.empty());
        ResponseEntity<?> response = authenticationService.login(request);
        assertEquals(401, response.getStatusCode().value());
    }
}
