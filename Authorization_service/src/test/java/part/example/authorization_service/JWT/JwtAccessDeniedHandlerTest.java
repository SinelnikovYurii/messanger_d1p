package part.example.authorization_service.JWT;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.mockito.Mockito.*;

class JwtAccessDeniedHandlerTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private AccessDeniedException accessDeniedException;
    @InjectMocks
    private JwtAccessDeniedHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this).close();
        handler = new JwtAccessDeniedHandler();
    }

    @Test
    void testHandle() throws IOException {
        handler.handle(request, response, accessDeniedException);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Access is denied");
    }
}
