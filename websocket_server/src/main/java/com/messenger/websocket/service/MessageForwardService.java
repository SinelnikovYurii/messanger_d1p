package websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Service;
import websocket.model.WebSocketMessage;
import websocket.model.MessageType;


import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageForwardService {

    private final String kafkaBootstrapServers = "localhost:9092";
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private KafkaConsumer<String, String> consumer;
    private volatile boolean running = false;
    private Thread consumerThread;

    public void startListening() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "websocket-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("chat-messages"));

        running = true;
        consumerThread = new Thread(this::consumeMessages);
        consumerThread.start();

        log.info("Started Kafka consumer for topic: chat-messages");
    }

    private void consumeMessages() {
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    handleMessageFromKafka(record.key(), record.value());
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Error consuming Kafka messages", e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessageFromKafka(String key, String message) {
        try {
            log.info("[KAFKA] Received message from Kafka - Key: {}, Message: {}", key, message);

            Map<String, Object> messageData = objectMapper.readValue(message, Map.class);

            Long chatId = null;
            if (key != null && !key.isEmpty()) {
                try {
                    chatId = Long.valueOf(key);
                    log.debug("[KAFKA] ChatId from key: {}", chatId);
                } catch (NumberFormatException e) {
                    log.warn("[KAFKA] Invalid key format: {}", key);
                }
            }

            if (chatId == null && messageData.containsKey("chatId")) {
                chatId = ((Number) messageData.get("chatId")).longValue();
                log.debug("[KAFKA] ChatId from message body: {}", chatId);
            }

            if (chatId == null) {
                log.warn("[KAFKA] Could not determine chatId from message: {}", message);
                return;
            }

            // Создаем WebSocketMessage со ВСЕМИ данными, включая файлы
            WebSocketMessage wsMessage = new WebSocketMessage();
            wsMessage.setType(MessageType.CHAT_MESSAGE);
            wsMessage.setContent((String) messageData.get("content"));
            wsMessage.setChatId(chatId);

            // Устанавливаем данные отправителя
            if (messageData.containsKey("senderId")) {
                wsMessage.setUserId(((Number) messageData.get("senderId")).longValue());
                wsMessage.setSenderId(((Number) messageData.get("senderId")).longValue());
            }
            if (messageData.containsKey("senderUsername")) {
                wsMessage.setUsername((String) messageData.get("senderUsername"));
                wsMessage.setSenderUsername((String) messageData.get("senderUsername"));
            }

            // ИСПРАВЛЕНО: Копируем данные о файлах из Kafka
            if (messageData.containsKey("messageType")) {
                wsMessage.setMessageType((String) messageData.get("messageType"));
                log.debug("[KAFKA] Message type: {}", messageData.get("messageType"));
            }
            if (messageData.containsKey("fileUrl")) {
                wsMessage.setFileUrl((String) messageData.get("fileUrl"));
                log.debug("[KAFKA] File URL: {}", messageData.get("fileUrl"));
            }
            if (messageData.containsKey("fileName")) {
                wsMessage.setFileName((String) messageData.get("fileName"));
                log.debug("[KAFKA] File name: {}", messageData.get("fileName"));
            }
            if (messageData.containsKey("fileSize")) {
                wsMessage.setFileSize(((Number) messageData.get("fileSize")).longValue());
                log.debug("[KAFKA] File size: {}", messageData.get("fileSize"));
            }
            if (messageData.containsKey("mimeType")) {
                wsMessage.setMimeType((String) messageData.get("mimeType"));
                log.debug("[KAFKA] MIME type: {}", messageData.get("mimeType"));
            }
            if (messageData.containsKey("thumbnailUrl") && messageData.get("thumbnailUrl") != null) {
                wsMessage.setThumbnailUrl((String) messageData.get("thumbnailUrl"));
                log.debug("[KAFKA] Thumbnail URL: {}", messageData.get("thumbnailUrl"));
            }
            if (messageData.containsKey("messageId")) {
                wsMessage.setId(((Number) messageData.get("messageId")).longValue());
            }

            log.info("[KAFKA] Processing message for chat {} from user {} (ID: {}): '{}' [Type: {}, HasFile: {}]",
                chatId, wsMessage.getUsername(), wsMessage.getUserId(), wsMessage.getContent(),
                wsMessage.getMessageType(), wsMessage.getFileUrl() != null);

            // Получаем каналы участников чата
            List<Channel> channels = sessionManager.getChatChannels(chatId);
            log.info("[SESSION] Found {} active channels for chat {}", channels.size(), chatId);

            if (!channels.isEmpty()) {
                String jsonMessage = objectMapper.writeValueAsString(wsMessage);
                log.debug("[FORWARD] JSON message to send: {}", jsonMessage);

                int successCount = 0;
                int failureCount = 0;

                for (Channel channel : channels) {
                    try {
                        if (channel.isActive()) {
                            channel.writeAndFlush(new TextWebSocketFrame(jsonMessage));
                            successCount++;
                            log.debug("[FORWARD] Sent message to channel: {}", channel.id().asShortText());
                        } else {
                            failureCount++;
                            log.warn("[FORWARD] Channel is not active: {}", channel.id().asShortText());
                        }
                    } catch (Exception e) {
                        failureCount++;
                        log.error("[FORWARD] Failed to send message to channel {}: {}",
                            channel.id().asShortText(), e.getMessage(), e);
                    }
                }

                log.info("[FORWARD] Message forwarding completed for chat {} - Success: {}, Failures: {}",
                    chatId, successCount, failureCount);
            } else {
                log.info("[FORWARD] No active channels for chat {} - message will not be delivered", chatId);
            }

        } catch (Exception e) {
            log.error("[KAFKA] Error processing message from Kafka: {}", message, e);
        }
    }

    public void stop() {
        running = false;
        if (consumer != null) {
            consumer.wakeup();
        }
        if (consumerThread != null) {
            try {
                consumerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (consumer != null) {
            consumer.close();
        }
        log.info("Kafka consumer stopped");
    }
}