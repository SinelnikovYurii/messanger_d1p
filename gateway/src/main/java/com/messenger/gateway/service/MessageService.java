package com.messenger.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    @Value("${message-service.url:http://localhost:8084}")
    private String messageServiceUrl;

    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Flux<Object> getChatMessages(Long chatId, String token) {
        return webClientBuilder.build()
                .get()
                .uri(messageServiceUrl + "/api/messages/chat/" + chatId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(Object.class)
                .doOnError(error -> log.error("Error fetching chat messages: {}", error.getMessage()));
    }

    public Mono<Object> sendMessage(Long chatId, Long senderId, String content, String token) {
        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("chatId", chatId);
        messageRequest.put("senderId", senderId);
        messageRequest.put("content", content);

        return webClientBuilder.build()
                .post()
                .uri(messageServiceUrl + "/api/messages")
                .header("Authorization", "Bearer " + token)
                .bodyValue(messageRequest)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnSuccess(message -> {
                    try {
                        // Отправляем уведомление в Kafka
                        kafkaTemplate.send("chat-messages", chatId.toString(), message);
                        log.info("Message sent to Kafka: chatId={}", chatId);
                    } catch (Exception e) {
                        log.error("Failed to send message to Kafka", e);
                    }
                })
                .doOnError(error -> log.error("Error sending message: {}", error.getMessage()));
    }

    public Mono<Object> getMessageById(Long messageId, String token) {
        return webClientBuilder.build()
                .get()
                .uri(messageServiceUrl + "/api/messages/" + messageId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Error fetching message: {}", error.getMessage()));
    }
}