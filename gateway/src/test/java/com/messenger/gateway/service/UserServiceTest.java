package com.messenger.gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test
    void testGetAllUsers() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(webClient).when(builder).build();
        doReturn(uriSpec).when(webClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString());
        doAnswer(invocation -> invocation.getMock()).when(headersSpec).header(anyString(), any());
        doAnswer(invocation -> invocation.getMock()).when(headersSpec).header(anyString(), any(String[].class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(Flux.empty()).when(responseSpec).bodyToFlux(Object.class);
        UserService service = new UserService(builder);
        Flux<Object> result = service.getAllUsers("token");
        assertNotNull(result);
        assertDoesNotThrow(() -> result.blockLast());
    }

    @Test
    void testGetUserById() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(webClient).when(builder).build();
        doReturn(uriSpec).when(webClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString());
        doAnswer(invocation -> invocation.getMock()).when(headersSpec).header(anyString(), any());
        doAnswer(invocation -> invocation.getMock()).when(headersSpec).header(anyString(), any(String[].class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(Mono.empty()).when(responseSpec).bodyToMono(Object.class);
        UserService service = new UserService(builder);
        Mono<Object> result = service.getUserById(1L, "token");
        assertNotNull(result);
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    void testUpdateUser() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(webClient).when(builder).build();
        doReturn(uriSpec).when(webClient).put();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doAnswer(invocation -> invocation.getMock()).when(uriSpec).header(anyString(), any());
        doAnswer(invocation -> invocation.getMock()).when(uriSpec).header(anyString(), any(String[].class));
        doAnswer(invocation -> invocation.getMock()).when(headersSpec).header(anyString(), any());
        doAnswer(invocation -> invocation.getMock()).when(headersSpec).header(anyString(), any(String[].class));
        doReturn(headersSpec).when(uriSpec).body(any(), eq(Object.class));
        doReturn(headersSpec).when(uriSpec).bodyValue(any());
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(Mono.empty()).when(responseSpec).bodyToMono(Object.class);
        UserService service = new UserService(builder);
        Mono<Object> result = service.updateUser(1L, new Object(), "token");
        assertNotNull(result);
        assertDoesNotThrow(() -> result.block());
    }
}
