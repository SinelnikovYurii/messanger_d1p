package websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@SpringBootApplication
@EnableCaching
public class WebSocketApplication {
    public static void main(String[] args) {
        log.info("[STARTUP] Starting WebSocket Server Application...");

        ConfigurableApplicationContext context = SpringApplication.run(WebSocketApplication.class, args);

        log.info("[STARTUP] WebSocket Server Application started successfully!");
        log.info("[STARTUP] Server is ready to accept WebSocket connections");
        log.info("[STARTUP] WebSocket endpoint: ws://localhost:8092/ws/chat");
        log.info("[STARTUP] Kafka integration enabled for topic: chat-messages");

        // Добавляем shutdown hook для graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[SHUTDOWN] Shutting down WebSocket Server Application...");
            context.close();
            log.info("[SHUTDOWN] WebSocket Server Application stopped gracefully");
        }));
    }
}
