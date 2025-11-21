package com.messenger.gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {
    @Test
    void testGetChatMessages() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(webClient).when(builder).build();
        doReturn(uriSpec).when(webClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString());
        doReturn(headersSpec).when(headersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(Flux.empty()).when(responseSpec).bodyToFlux(Object.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        MessageService service = new MessageService(builder, kafka);
        Flux<Object> result = service.getChatMessages(1L, "token");
        assertNotNull(result);
        assertDoesNotThrow(() -> result.blockLast());
    }

    @Test
    void testSendMessage() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(webClient).when(builder).build();
        doReturn(uriSpec).when(webClient).post();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doReturn(uriSpec).when(uriSpec).header(anyString(), anyString());
        doReturn(headersSpec).when(uriSpec).body(any(), eq(Object.class));
        doReturn(headersSpec).when(uriSpec).bodyValue(any());
        doReturn(headersSpec).when(headersSpec).header(anyString(), anyString()); // <--- финальное исправление
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(Mono.empty()).when(responseSpec).bodyToMono(Object.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        MessageService service = new MessageService(builder, kafka);
        Mono<Object> result = service.sendMessage(1L, 2L, "hello", "token");
        assertNotNull(result);
        assertDoesNotThrow(() -> result.block());
    }
}
