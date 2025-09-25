package com.messenger.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
public class TestWebSocketHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());

        return session.send(
                session.receive()
                        .doOnNext(message -> log.info("Received message: {}", message.getPayloadAsText()))
                        .map(message -> session.textMessage("Echo: " + message.getPayloadAsText()))
        ).doOnTerminate(() -> log.info("WebSocket connection terminated: {}", session.getId()));
    }
}
