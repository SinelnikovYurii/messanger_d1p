package com.messenger.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

class JwtAuthenticationFilterTest {
    @Mock
    private JwtService jwtService;
    @Mock
    private WebFilterChain webFilterChain;
    @Mock
    private ServerWebExchange exchange;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private ServerHttpRequest.Builder requestBuilder;
    @Mock
    private ServerWebExchange.Builder exchangeBuilder;
    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Используем spy для фильтра, чтобы замокать unauthorizedResponse
        filter = spy(new JwtAuthenticationFilter(jwtService));
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getQueryParams()).thenReturn(new org.springframework.util.LinkedMultiValueMap<>());
        when(response.bufferFactory()).thenReturn(new DefaultDataBufferFactory());
        // Мокаем цепочку mutate().header().build() для запроса
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), any())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(request);
        // Мокаем цепочку mutate().request().build() для exchange
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(exchange);
        // Мокаем unauthorizedResponse для всех случаев
        doReturn(Mono.empty()).when(filter).unauthorizedResponse(any(), anyString());
    }

    @Test
    void testFilter_ValidToken() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer validtoken"); }});
        when(jwtService.extractUsername("validtoken")).thenReturn("testuser");
        when(jwtService.extractUserId("validtoken")).thenReturn(1L);
        when(jwtService.isTokenValid("validtoken")).thenReturn(true);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(any());
    }

    @Test
    void testFilter_NoAuthHeader() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(webFilterChain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(exchange);
    }

    @Test
    void testFilter_AuthHeaderNotBearer() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Basic sometoken"); }});
        when(webFilterChain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(exchange);
    }

    @Test
    void testFilter_ExpiredToken() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer expiredtoken"); }});
        when(jwtService.isTokenValid("expiredtoken")).thenReturn(false);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain, never()).filter(any());
    }

    @Test
    void testFilter_MalformedToken() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer malformedtoken"); }});
        when(jwtService.isTokenValid("malformedtoken")).thenThrow(new RuntimeException("Malformed token"));
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain, never()).filter(any());
    }

    @Test
    void testFilter_PublicPath() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/public/test", ""));
        when(webFilterChain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(exchange);
    }

    @Test
    void testFilter_OptionsRequest() {
        when(request.getMethod()).thenReturn(HttpMethod.OPTIONS);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(webFilterChain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(exchange);
    }

    @Test
    void testFilter_TokenInQueryParam() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("token", "validtoken");
        when(request.getQueryParams()).thenReturn(params);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(jwtService.isTokenValid("validtoken")).thenReturn(true);
        when(jwtService.extractUsername("validtoken")).thenReturn("testuser");
        when(jwtService.extractUserId("validtoken")).thenReturn(1L);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(any());
    }

    @Test
    void testFilter_AuthorizationHeaderWithSpace() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer "); }});
        filter.filter(exchange, webFilterChain).block();
        verify(filter).unauthorizedResponse(eq(exchange), anyString());
        verify(webFilterChain, never()).filter(exchange);
    }

    @Test
    void testFilter_EmptyQueryParams() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getQueryParams()).thenReturn(new org.springframework.util.LinkedMultiValueMap<>());
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(webFilterChain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(exchange);
    }

    @Test
    void testFilter_BruteForceInvalidTokens() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        for (int i = 0; i < 10; i++) {
            String token = "invalidtoken" + i;
            when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer " + token); }});
            when(jwtService.isTokenValid(token)).thenReturn(false);
            filter.filter(exchange, webFilterChain).block();
        }
        verify(webFilterChain, never()).filter(any());
    }

    @Test
    void testFilter_ValidToken_MutatedHeaders() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer validtoken"); }});
        when(jwtService.isTokenValid("validtoken")).thenReturn(true);
        when(jwtService.extractUsername("validtoken")).thenReturn("testuser");
        when(jwtService.extractUserId("validtoken")).thenReturn(123L);
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header("X-User-Id", "123")).thenReturn(requestBuilder);
        when(requestBuilder.header("X-Username", "testuser")).thenReturn(requestBuilder);
        when(requestBuilder.header("Authorization", "Bearer validtoken")).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(request);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(request)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(exchange);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(requestBuilder).header("X-User-Id", "123");
        verify(requestBuilder).header("X-Username", "testuser");
        verify(requestBuilder).header("Authorization", "Bearer validtoken");
        verify(webFilterChain).filter(any());
    }

    @Test
    void testFilter_AuthenticationContextWrite() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer validtoken"); }});
        when(jwtService.isTokenValid("validtoken")).thenReturn(true);
        when(jwtService.extractUsername("validtoken")).thenReturn("testuser");
        when(jwtService.extractUserId("validtoken")).thenReturn(123L);
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(request);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(request)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(exchange);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        Mono<Void> result = filter.filter(exchange, webFilterChain);
        assertNotNull(result.contextWrite(ctx -> {
            assertTrue(ctx.hasKey(org.springframework.security.core.context.SecurityContext.class));
            return ctx;
        }));
    }

    @Test
    void testFilter_ExceptionOnUserId() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer validtoken"); }});
        when(jwtService.isTokenValid("validtoken")).thenReturn(true);
        when(jwtService.extractUsername("validtoken")).thenReturn("testuser");
        when(jwtService.extractUserId("validtoken")).thenThrow(new RuntimeException("userId error"));
        filter.filter(exchange, webFilterChain).block();
        verify(filter).unauthorizedResponse(eq(exchange), contains("userId error"));
    }

    @Test
    void testFilter_TokenInHeaderAndQuery_HeaderPriority() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("token", "querytoken");
        when(request.getQueryParams()).thenReturn(params);
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer headertoken"); }});
        when(jwtService.isTokenValid("headertoken")).thenReturn(true);
        when(jwtService.extractUsername("headertoken")).thenReturn("testuser");
        when(jwtService.extractUserId("headertoken")).thenReturn(1L);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(jwtService).isTokenValid("headertoken");
        verify(jwtService, never()).isTokenValid("querytoken");
    }

    @Test
    void testFilter_AllPublicPaths() {
        String[] paths = {"/", "/auth/login", "/public/resource", "/actuator/health", "/debug/info"};
        for (String path : paths) {
            when(request.getMethod()).thenReturn(HttpMethod.GET);
            when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse(path, ""));
            when(webFilterChain.filter(exchange)).thenReturn(Mono.empty());
            filter.filter(exchange, webFilterChain).block();
        }
        verify(webFilterChain, times(paths.length)).filter(exchange);
    }

    @Test
    void testFilter_PostMethod() {
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        when(request.getHeaders()).thenReturn(new HttpHeaders() {{ set("Authorization", "Bearer validtoken"); }});
        when(jwtService.isTokenValid("validtoken")).thenReturn(true);
        when(jwtService.extractUsername("validtoken")).thenReturn("testuser");
        when(jwtService.extractUserId("validtoken")).thenReturn(1L);
        when(webFilterChain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, webFilterChain).block();
        verify(webFilterChain).filter(any());
    }

    @Test
    void testFilter_EmptyTokenInQueryParam() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse("/api/test", ""));
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("token", "");
        when(request.getQueryParams()).thenReturn(params);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        filter.filter(exchange, webFilterChain).block();
        verify(filter).unauthorizedResponse(eq(exchange), anyString());
        verify(webFilterChain, never()).filter(any());
    }
}
