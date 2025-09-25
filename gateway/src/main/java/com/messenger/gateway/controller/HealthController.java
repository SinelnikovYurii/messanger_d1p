package com.messenger.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("API Gateway is running"));
    }

    @GetMapping("/")
    public Mono<ResponseEntity<String>> root() {
        return Mono.just(ResponseEntity.ok("Messenger API Gateway"));
    }
}
