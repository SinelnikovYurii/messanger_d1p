package part.example.authorization_service.JWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import part.example.authorization_service.models.User;
import part.example.authorization_service.repository.UserRepository;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain filterChain;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtAuthFilter = new JwtAuthFilter(jwtUtil, userRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testDoFilterInternal_ValidateEndpoint() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/auth/validate");
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilterInternal_ValidToken() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/other");
        when(request.getHeader("Authorization")).thenReturn("Bearer validtoken");
        User user = new User();
        user.setUsername("testuser");
        when(jwtUtil.extractAllClaims("validtoken")).thenReturn(Mockito.mock(io.jsonwebtoken.Claims.class));
        when(jwtUtil.extractAllClaims("validtoken").getSubject()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtUtil.validateToken("validtoken", user)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_InvalidToken() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/other");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalidtoken");
        User user = new User();
        user.setUsername("testuser");
        when(jwtUtil.extractAllClaims("invalidtoken")).thenReturn(Mockito.mock(io.jsonwebtoken.Claims.class));
        when(jwtUtil.extractAllClaims("invalidtoken").getSubject()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtUtil.validateToken("invalidtoken", user)).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_NoAuthHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/other");
        when(request.getHeader("Authorization")).thenReturn(null);
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_AuthHeaderNotBearer() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/other");
        when(request.getHeader("Authorization")).thenReturn("Basic sometoken");
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}

