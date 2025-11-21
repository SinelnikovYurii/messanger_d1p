package com.messenger.gateway.service;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    @Value("${auth-service.url}")
    private String authServiceUrl;

    private final WebClient.Builder webClientBuilder;

    public Mono<Long> validateAndGetUserId(String token) {
        return webClientBuilder.build()
                .get()
                .uri(authServiceUrl + "/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Long.class)
                .doOnSuccess(userId -> log.info("Token validated successfully for user: {}", userId))
                .doOnError(error -> log.error("Token validation failed: {}", error.getMessage()))
                .onErrorReturn(-1L);
    }
}
