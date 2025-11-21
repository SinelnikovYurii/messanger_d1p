package part.example.authorization_service.cotroller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import part.example.authorization_service.DTO.LoginRequest;
import part.example.authorization_service.DTO.RegisterRequest;
import part.example.authorization_service.JWT.JwtUtil;
import part.example.authorization_service.repository.UserRepository;
import part.example.authorization_service.service.AuthenticationService;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Mock
    private AuthenticationService authService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private Authentication authentication;
    @InjectMocks
    private AuthController controller;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this).close();
        controller = new AuthController();
        // Установка приватных полей через рефлексию
        java.lang.reflect.Field f1 = AuthController.class.getDeclaredField("authService");
        f1.setAccessible(true);
        f1.set(controller, authService);
        java.lang.reflect.Field f2 = AuthController.class.getDeclaredField("userRepository");
        f2.setAccessible(true);
        f2.set(controller, userRepository);
        java.lang.reflect.Field f3 = AuthController.class.getDeclaredField("jwtUtil");
        f3.setAccessible(true);
        f3.set(controller, jwtUtil);
    }

    @Test
    void testRegister() {
        RegisterRequest request = new RegisterRequest();
        ResponseEntity<?> expected = ResponseEntity.ok("registered");
        doReturn(expected).when(authService).register(request);
        assertEquals(expected, controller.register(request));
    }

    @Test
    void testLogin() {
        LoginRequest request = new LoginRequest();
        ResponseEntity<?> expected = ResponseEntity.ok("logged in");
        doReturn(expected).when(authService).login(request);
        assertEquals(expected, controller.login(request));
    }

    @Test
    void testGetUserInfo_ValidUser() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User("user", "pass", Collections.emptyList());
        part.example.authorization_service.models.User appUser = new part.example.authorization_service.models.User("user", "pass", "email", "first", "last");
        java.lang.reflect.Field idField;
        try {
            idField = part.example.authorization_service.models.User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(appUser, 1L);
        } catch (Exception e) { throw new RuntimeException(e); }
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(appUser));
        ResponseEntity<?> response = controller.getUserInfo(authentication);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((java.util.Map<?,?>)response.getBody()).containsKey("userId"));
    }

    @Test
    void testGetUserInfo_UserNotFound() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User("user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findByUsername("user")).thenReturn(Optional.empty());
        ResponseEntity<?> response = controller.getUserInfo(authentication);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((java.util.Map<?,?>)response.getBody()).containsKey("error"));
    }

    @Test
    void testGetUserInfo_Unauthorized() {
        when(authentication.getPrincipal()).thenReturn("notUserDetails");
        ResponseEntity<?> response = controller.getUserInfo(authentication);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((java.util.Map<?,?>)response.getBody()).containsKey("error"));
    }

    @Test
    void testGetUserInfo_NullAuth() {
        ResponseEntity<?> response = controller.getUserInfo(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(((java.util.Map<?,?>)response.getBody()).containsKey("error"));
    }

    @Test
    void testGetCurrentUser_Valid() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User("user", "pass", Collections.emptyList());
        part.example.authorization_service.models.User appUser = new part.example.authorization_service.models.User("user", "pass", "email", "first", "last");
        java.lang.reflect.Field idField;
        try {
            idField = part.example.authorization_service.models.User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(appUser, 2L);
        } catch (Exception e) { throw new RuntimeException(e); }
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(appUser));
        ResponseEntity<Long> response = controller.getCurrentUser(authentication);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getBody());
    }

    @Test
    void testGetCurrentUser_NotFound() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User("user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findByUsername("user")).thenReturn(Optional.empty());
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
    }

    @Test
    void testGetCurrentUser_NullAuth() {
        ResponseEntity<Long> response = controller.getCurrentUser(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void testValidateToken_Valid() {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User("user", "pass", Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(userDetails);
        ResponseEntity<?> response = controller.validateToken(authentication);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("valid", ((java.util.Map<?,?>)response.getBody()).get("status"));
    }

    @Test
    void testValidateToken_Invalid() {
        when(authentication.getPrincipal()).thenReturn("notUserDetails");
        ResponseEntity<?> response = controller.validateToken(authentication);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid token", ((java.util.Map<?,?>)response.getBody()).get("error"));
    }

    @Test
    void testValidateToken_NullAuth() {
        ResponseEntity<?> response = controller.validateToken(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid token", ((java.util.Map<?,?>)response.getBody()).get("error"));
    }
}
