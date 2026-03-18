package part.example.authorization_service.cotroller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Mock
    private AuthenticationService authService;

    @Mock
    private Authentication authentication;

    private AuthController controller;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this).close();
        controller = new AuthController(authService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testRegister_success() {
        RegisterRequest request = new RegisterRequest("user", "pass", "u@example.com", null, null);
        RegisterResult result = new RegisterResult("jwt-token", 1L);
        when(authService.register(request)).thenReturn(result);

        ResponseEntity<?> response = controller.register(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("jwt-token", body.get("token"));
        assertEquals(1L,          body.get("userId"));
        assertEquals("Пользователь успешно зарегистрирован", body.get("message"));
        verify(authService).register(request);
    }

    @Test
    void testRegister_invalidRequest_returns400() {
        RegisterRequest request = new RegisterRequest("", "pass", "u@example.com", null, null);
        when(authService.register(request))
                .thenThrow(new InvalidRequestException("Имя пользователя обязательно"));

        ResponseEntity<?> response = controller.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Имя пользователя обязательно",
                ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void testRegister_userAlreadyExists_returns409() {
        RegisterRequest request = new RegisterRequest("existing", "pass", "u@example.com", null, null);
        when(authService.register(request))
                .thenThrow(new UserAlreadyExistsException("Пользователь с таким именем уже существует"));

        ResponseEntity<?> response = controller.register(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Пользователь с таким именем уже существует",
                ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void testRegister_internalError_returns500WithSafeMessage() {
        RegisterRequest request = new RegisterRequest("user", "pass", "u@example.com", null, null);
        when(authService.register(request))
                .thenThrow(new RuntimeException("SQL: connection refused"));

        ResponseEntity<?> response = controller.register(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        // Внутренние детали не должны утекать клиенту
        String errorMsg = (String) ((Map<?, ?>) response.getBody()).get("error");
        assertNotNull(errorMsg);
        assertFalse(errorMsg.contains("SQL"));
        assertFalse(errorMsg.contains("connection refused"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testLogin_success() {
        LoginRequest request = new LoginRequest("user", "pass");
        Map<String, Object> userInfo = Map.of("id", 1L, "username", "user");
        LoginResult result = new LoginResult("jwt-token", userInfo);
        when(authService.login(request)).thenReturn(result);

        ResponseEntity<?> response = controller.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("jwt-token",          body.get("token"));
        assertEquals(userInfo,             body.get("user"));
        assertEquals("Успешная авторизация", body.get("message"));
        verify(authService).login(request);
    }

    @Test
    void testLogin_invalidRequest_returns400() {
        LoginRequest request = new LoginRequest("", "pass");
        when(authService.login(request))
                .thenThrow(new InvalidRequestException("Имя пользователя обязательно"));

        ResponseEntity<?> response = controller.login(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Имя пользователя обязательно",
                ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void testLogin_badCredentials_returns401() {
        LoginRequest request = new LoginRequest("user", "wrong");
        when(authService.login(request))
                .thenThrow(new BadCredentialsException("Неверный логин или пароль"));

        ResponseEntity<?> response = controller.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Неверный логин или пароль",
                ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void testLogin_internalError_returns500WithSafeMessage() {
        LoginRequest request = new LoginRequest("user", "pass");
        when(authService.login(request))
                .thenThrow(new RuntimeException("DB connection timeout"));

        ResponseEntity<?> response = controller.login(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        String errorMsg = (String) ((Map<?, ?>) response.getBody()).get("error");
        assertNotNull(errorMsg);
        assertFalse(errorMsg.contains("DB connection timeout"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserInfo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testGetUserInfo_ValidUser() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authService.getUserInfo("user"))
                .thenReturn(Optional.of(Map.of("userId", 1L, "username", "user")));

        ResponseEntity<?> response = controller.getUserInfo(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("userId"));
        verify(authService).getUserInfo("user");
    }

    @Test
    void testGetUserInfo_UserNotFound() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authService.getUserInfo("user")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getUserInfo(authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
    }

    @Test
    void testGetUserInfo_Unauthorized() {
        when(authentication.getPrincipal()).thenReturn("notUserDetails");

        ResponseEntity<?> response = controller.getUserInfo(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
        verifyNoInteractions(authService);
    }

    @Test
    void testGetUserInfo_NullAuth() {
        ResponseEntity<?> response = controller.getUserInfo(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
        verifyNoInteractions(authService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCurrentUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testGetCurrentUser_Valid() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authService.getCurrentUserId("user")).thenReturn(Optional.of(2L));

        ResponseEntity<Long> response = controller.getCurrentUser(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getBody());
        verify(authService).getCurrentUserId("user");
    }

    @Test
    void testGetCurrentUser_NotFound() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authService.getCurrentUserId("user")).thenReturn(Optional.empty());

        ResponseEntity<Long> response = controller.getCurrentUser(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void testGetCurrentUser_Unauthorized() {
        when(authentication.getPrincipal()).thenReturn("notUserDetails");

        ResponseEntity<Long> response = controller.getCurrentUser(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verifyNoInteractions(authService);
    }

    @Test
    void testGetCurrentUser_NullAuth() {
        ResponseEntity<Long> response = controller.getCurrentUser(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verifyNoInteractions(authService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateToken
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testValidateToken_Valid() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);

        ResponseEntity<?> response = controller.validateToken(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("valid", ((Map<?, ?>) response.getBody()).get("status"));
    }

    @Test
    void testValidateToken_Invalid() {
        when(authentication.getPrincipal()).thenReturn("notUserDetails");

        ResponseEntity<?> response = controller.validateToken(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid token", ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void testValidateToken_NullAuth() {
        ResponseEntity<?> response = controller.validateToken(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid token", ((Map<?, ?>) response.getBody()).get("error"));
    }
}
