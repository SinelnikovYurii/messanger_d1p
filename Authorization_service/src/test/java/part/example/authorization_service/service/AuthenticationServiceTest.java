package part.example.authorization_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private AuthenticationService authenticationService;


    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("newuser", "secret", "new@example.com", "Ivan", "Ivanov");

        Mockito.when(userRepository.existsByUsername("newuser")).thenReturn(false);
        Mockito.when(passwordEncoder.encode("secret")).thenReturn("encodedPassword");
        Mockito.when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        mockWebClientChain();

        RegisterResult result = authenticationService.register(request);

        assertNotNull(result);
        assertEquals("jwt-token", result.getToken());
        assertEquals(1L, result.getUserId());
    }

    @Test
    void register_userAlreadyExists_throwsUserAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest("existing", "secret", "e@example.com", null, null);

        Mockito.when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> authenticationService.register(request));
    }

    @Test
    void register_emptyUsername_throwsInvalidRequestException() {
        RegisterRequest request = new RegisterRequest("", "secret", "e@example.com", null, null);

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.register(request));

        // До БД дело не дошло — валидация сработала раньше
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void register_nullUsername_throwsInvalidRequestException() {
        RegisterRequest request = new RegisterRequest(null, "secret", "e@example.com", null, null);

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.register(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void register_emptyPassword_throwsInvalidRequestException() {
        RegisterRequest request = new RegisterRequest("user", "", "e@example.com", null, null);

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.register(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void register_emptyEmail_throwsInvalidRequestException() {
        RegisterRequest request = new RegisterRequest("user", "secret", "", null, null);

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.register(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void register_nullEmail_throwsInvalidRequestException() {
        RegisterRequest request = new RegisterRequest("user", "secret", null, null, null);

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.register(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void register_repositoryException_propagates() {
        RegisterRequest request = new RegisterRequest("user", "secret", "e@example.com", null, null);

        Mockito.when(userRepository.existsByUsername("user")).thenReturn(false);
        Mockito.when(passwordEncoder.encode(any())).thenReturn("encoded");
        Mockito.when(userRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authenticationService.register(request));
        assertEquals("DB error", ex.getMessage());
    }


    @Test
    void register_preKeyBundleError_doesNotFailRegistration() {
        RegisterRequest request = new RegisterRequest("user", "secret", "e@example.com", null, null);

        Mockito.when(userRepository.existsByUsername("user")).thenReturn(false);
        Mockito.when(passwordEncoder.encode(any())).thenReturn("encoded");
        Mockito.when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });
        Mockito.when(jwtUtil.generateToken(any())).thenReturn("jwt-token");

        // WebClient возвращает ошибку — регистрация всё равно успешна
        Mockito.when(webClientBuilder.build()).thenReturn(webClient);
        Mockito.when(webClient.post()).thenReturn(requestBodyUriSpec);
        Mockito.when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        Mockito.when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        Mockito.doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        Mockito.when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        Mockito.when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null)));

        RegisterResult result = authenticationService.register(request);

        assertNotNull(result);
        assertEquals("jwt-token", result.getToken());
        assertEquals(2L, result.getUserId());
    }


    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("user", "pass");

        User user = new User("user", "encoded", "test@example.com", "Test", "User");
        user.setId(1L);

        Mockito.when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        Mockito.when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");

        LoginResult result = authenticationService.login(request);

        assertNotNull(result);
        assertEquals("jwt-token", result.getToken());
        assertNotNull(result.getUserInfo());
        Map<String, Object> info = result.getUserInfo();
        assertEquals(1L,     info.get("id"));
        assertEquals("user", info.get("username"));
        assertEquals("test@example.com", info.get("email"));
    }


    @Test
    void login_wrongPassword_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest("user", "wrong");

        User user = new User("user", "encoded", null, null, null);
        Mockito.when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authenticationService.login(request));
    }

    @Test
    void login_userNotFound_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest("nouser", "pass");

        Mockito.when(userRepository.findByUsername("nouser")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authenticationService.login(request));
    }

    @Test
    void login_emptyUsername_throwsInvalidRequestException() {
        LoginRequest request = new LoginRequest("", "pass");

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.login(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void login_nullUsername_throwsInvalidRequestException() {
        LoginRequest request = new LoginRequest(null, "pass");

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.login(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void login_emptyPassword_throwsInvalidRequestException() {
        LoginRequest request = new LoginRequest("user", "");

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.login(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void login_nullPassword_throwsInvalidRequestException() {
        LoginRequest request = new LoginRequest("user", null);

        assertThrows(InvalidRequestException.class,
                () -> authenticationService.login(request));
        Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void login_repositoryException_propagates() {
        LoginRequest request = new LoginRequest("user", "pass");

        Mockito.when(userRepository.findByUsername("user"))
                .thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authenticationService.login(request));
        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void getUserInfo_found() {
        User user = new User("alice", "encoded", "alice@example.com", "Alice", "Smith");
        user.setId(5L);
        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Optional<Map<String, Object>> result = authenticationService.getUserInfo("alice");

        assertTrue(result.isPresent());
        assertEquals(5L,      result.get().get("userId"));
        assertEquals("alice", result.get().get("username"));
    }

    @Test
    void getUserInfo_notFound() {
        Mockito.when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<Map<String, Object>> result = authenticationService.getUserInfo("ghost");

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentUserId_found() {
        User user = new User("bob", "encoded", "bob@example.com", null, null);
        user.setId(7L);
        Mockito.when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        Optional<Long> result = authenticationService.getCurrentUserId("bob");

        assertTrue(result.isPresent());
        assertEquals(7L, result.get());
    }

    @Test
    void getCurrentUserId_notFound() {
        Mockito.when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<Long> result = authenticationService.getCurrentUserId("ghost");

        assertTrue(result.isEmpty());
    }

    /** Настроить успешную цепочку WebClient для тестов регистрации. */
    private void mockWebClientChain() {
        Mockito.when(webClientBuilder.build()).thenReturn(webClient);
        Mockito.when(webClient.post()).thenReturn(requestBodyUriSpec);
        Mockito.when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        Mockito.when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        Mockito.doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        Mockito.when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("ok"));
    }
}
