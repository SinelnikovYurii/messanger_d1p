package part.example.authorization_service.JWT;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

import static org.mockito.Mockito.*;

class JwtAuthenticationEntryPointTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private AuthenticationException authException;
    @InjectMocks
    private JwtAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this).close();
        entryPoint = new JwtAuthenticationEntryPoint();
    }

    @Test
    void testCommence() throws IOException {
        entryPoint.commence(request, response, authException);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: JWT token is invalid or missing");
    }
}
