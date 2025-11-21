package com.messenger.gateway.config;

import com.messenger.gateway.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityConfigTest {
    @Test
    void testSecurityWebFilterChain() {
        JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);
        SecurityConfig config = new SecurityConfig(filter);
        ServerHttpSecurity http = ServerHttpSecurity.http();
        SecurityWebFilterChain chain = config.securityWebFilterChain(http);
        assertNotNull(chain);
    }
}
