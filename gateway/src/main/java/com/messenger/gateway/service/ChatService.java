package com.messenger.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    @Value("${chat-service.url:http://localhost:8083}")
    private String chatServiceUrl;

    private final WebClient.Builder webClientBuilder;

    public Flux<Object> getUserChats(Long userId, String token) {
        return webClientBuilder.build()
                .get()
                .uri(chatServiceUrl + "/api/chats/user/" + userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(Object.class)
                .doOnError(error -> log.error("Error fetching user chats: {}", error.getMessage()));
    }

    public Mono<Object> createChat(Object request, String token) {
        return webClientBuilder.build()
                .post()
                .uri(chatServiceUrl + "/api/chats")
                .header("Authorization", "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Error creating chat: {}", error.getMessage()));
    }

    public Mono<Object> getChatById(Long chatId, String token) {
        return webClientBuilder.build()
                .get()
                .uri(chatServiceUrl + "/api/chats/" + chatId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Error fetching chat: {}", error.getMessage()));
    }
}
