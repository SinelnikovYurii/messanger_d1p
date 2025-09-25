package com.messenger.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
@Slf4j
public class DebugController {

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("gateway", "running");
        status.put("port", 8080);
        status.put("websocket_target", "ws://localhost:8092");
        status.put("timestamp", System.currentTimeMillis());

        log.info("Debug status requested");
        return Mono.just(ResponseEntity.ok(status));
    }

    @GetMapping("/routes")
    public Mono<ResponseEntity<Map<String, String>>> getRoutes() {
        Map<String, String> routes = new HashMap<>();
        routes.put("/ws/**", "ws://localhost:8092");
        routes.put("/auth/**", "http://localhost:8081");
        routes.put("/api/chats/**", "http://localhost:8083");
        routes.put("/api/messages/**", "http://localhost:8084");
        routes.put("/api/users/**", "http://localhost:8085");

        return Mono.just(ResponseEntity.ok(routes));
    }
}

