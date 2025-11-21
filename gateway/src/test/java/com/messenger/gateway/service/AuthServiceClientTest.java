package com.messenger.gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceClientTest {
    @Test
    void testValidateAndGetUserId() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(webClient).when(builder).build();
        doReturn(uriSpec).when(webClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString());
        doReturn(headersSpec).when(headersSpec).header(anyString(), anyString());
        doReturn(headersSpec).when(headersSpec).header(anyString(), any(String[].class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        // Успешный сценарий: bodyToMono возвращает корректное значение
        doReturn(Mono.just(123L)).when(responseSpec).bodyToMono(Long.class);
        AuthServiceClient client = new AuthServiceClient(builder);
        Mono<Long> result = client.validateAndGetUserId("token");
        assertNotNull(result);
        assertEquals(123L, result.block());
        // Проверка fallback: bodyToMono выбрасывает ошибку, onErrorReturn возвращает -1L
        doReturn(Mono.error(new RuntimeException("fail"))).when(responseSpec).bodyToMono(Long.class);
        Mono<Long> errorResult = client.validateAndGetUserId("token");
        assertNotNull(errorResult);
        assertEquals(-1L, errorResult.block());
    }
}
