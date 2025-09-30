package websocket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import websocket.service.JwtAuthService;
import websocket.service.MessageForwardService;
import websocket.service.SessionManager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
@Slf4j
public class WebSocketConfig {

    @Value("${server.port:8092}")
    private int webSocketPort;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JwtAuthService jwtAuthService;

    @Autowired
    private MessageForwardService messageForwardService;

    private websocket.WebSocketServer webSocketServer;
    private Thread webSocketThread;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Отключаем сериализацию дат как timestamp
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @PostConstruct
    public void startWebSocketServer() {
        try {
            log.info("[CONFIG] Initializing WebSocket server with Kafka integration...");

            webSocketServer = new websocket.WebSocketServer(webSocketPort, jwtAuthService, objectMapper(), kafkaTemplate, sessionManager());

            webSocketThread = new Thread(() -> {
                try {
                    webSocketServer.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("[CONFIG] WebSocket server interrupted");
                } catch (Exception e) {
                    log.error("[CONFIG] Failed to start WebSocket server", e);
                }
            });

            webSocketThread.setDaemon(false);
            webSocketThread.setName("WebSocketServerThread");
            webSocketThread.start();

            messageForwardService.startListening();

            log.info("[CONFIG] WebSocket server configuration completed on port {}", webSocketPort);

        } catch (Exception e) {
            log.error("[CONFIG] Failed to initialize WebSocket server", e);
        }
    }

    @PreDestroy
    public void stopWebSocketServer() {
        log.info("[CONFIG] Shutting down WebSocket server...");

        if (messageForwardService != null) {
            try {
                messageForwardService.stop();
                log.info("[CONFIG] MessageForwardService stopped");
            } catch (Exception e) {
                log.error("[CONFIG] Error stopping MessageForwardService", e);
            }
        }

        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
                log.info("[CONFIG] WebSocket server stopped");
            } catch (Exception e) {
                log.error("[CONFIG] Error stopping WebSocket server", e);
            }
        }

        if (webSocketThread != null && webSocketThread.isAlive()) {
            webSocketThread.interrupt();
        }

        log.info("[CONFIG] WebSocket server shutdown completed");
    }
}