package websocket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import websocket.service.JwtAuthService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
@Slf4j
public class WebSocketConfig {

    @Value("${websocket.jwt.secret}")
    private String jwtSecret;

    @Value("${websocket.port:8091}")
    private int webSocketPort;

    private websocket.WebSocketServer webSocketServer;
    private Thread webSocketThread;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public JwtAuthService jwtAuthService() {
        return new JwtAuthService(jwtSecret);
    }

    @PostConstruct
    public void startWebSocketServer() {
        try {

            JwtAuthService jwtAuthService = new JwtAuthService(jwtSecret);
            ObjectMapper objectMapper = new ObjectMapper();

            webSocketServer = new websocket.WebSocketServer(webSocketPort, jwtAuthService, objectMapper);

            webSocketThread = new Thread(() -> {
                try {
                    webSocketServer.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("WebSocket server interrupted");
                } catch (Exception e) {
                    log.error("Failed to start WebSocket server", e);
                }
            });

            webSocketThread.setDaemon(false);
            webSocketThread.setName("WebSocketServerThread");
            webSocketThread.start();

            log.info("WebSocket server starting on port {}", webSocketPort);

        } catch (Exception e) {
            log.error("Failed to initialize WebSocket server", e);
        }
    }

    @PreDestroy
    public void stopWebSocketServer() {
        log.info("Shutting down WebSocket server...");

        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (Exception e) {
                log.error("Error stopping WebSocket server", e);
            }
        }

        if (webSocketThread != null && webSocketThread.isAlive()) {
            webSocketThread.interrupt();
        }

        log.info("WebSocket server stopped");
    }
}