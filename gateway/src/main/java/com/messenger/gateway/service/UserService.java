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
public class UserService {

    @Value("${user-service.url:http://localhost:8082}")
    private String userServiceUrl;

    private final WebClient.Builder webClientBuilder;

    public Flux<Object> getAllUsers(String token) {
        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/api/users")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(Object.class)
                .doOnError(error -> log.error("Error fetching all users: {}", error.getMessage()));
    }

    public Mono<Object> getUserById(Long id, String token) {
        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/api/users/" + id)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Error fetching user by id {}: {}", id, error.getMessage()));
    }

    public Mono<Object> updateUser(Long id, Object userRequest, String token) {
        return webClientBuilder.build()
                .put()
                .uri(userServiceUrl + "/api/users/" + id)
                .header("Authorization", "Bearer " + token)
                .bodyValue(userRequest)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Error updating user {}: {}", id, error.getMessage()));
    }

    public Flux<Object> searchUsers(String query, String token) {
        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/api/users/search?q=" + query)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(Object.class)
                .doOnError(error -> log.error("Error searching users: {}", error.getMessage()));
    }
}