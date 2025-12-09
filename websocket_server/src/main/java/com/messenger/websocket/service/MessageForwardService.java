package com.messenger.websocket.service;

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
import com.messenger.websocket.model.WebSocketMessage;
import com.messenger.websocket.model.MessageType;


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
    public void handleMessageFromKafka(String key, String message) {
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
                Object chatIdObj = messageData.get("chatId");
                if (chatIdObj instanceof Number) {
                    chatId = ((Number) chatIdObj).longValue();
                } else if (chatIdObj instanceof String) {
                    try {
                        chatId = Long.parseLong((String) chatIdObj);
                    } catch (NumberFormatException e) {
                        log.warn("[KAFKA] chatId as String is not a number: {}", chatIdObj);
                    }
                }
                log.debug("[KAFKA] ChatId from message body: {}", chatId);
            }

            if (chatId == null) {
                log.warn("[KAFKA] Could not determine chatId from message: {}", message);
                return;
            }

            WebSocketMessage wsMessage = new WebSocketMessage();


            Object messageTypeObj = messageData.get("type");
            String messageTypeStr = null;
            if (messageTypeObj instanceof String) {
                messageTypeStr = (String) messageTypeObj;
            } else if (messageTypeObj != null) {
                messageTypeStr = messageTypeObj.toString();
            }
            MessageType messageType = MessageType.CHAT_MESSAGE;
            if (messageTypeStr != null) {
                try {
                    messageType = MessageType.valueOf(messageTypeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("[KAFKA] Unknown message type: {}", messageTypeStr);
                }
            }
            wsMessage.setType(messageType);
            wsMessage.setContent((String) messageData.get("content"));
            wsMessage.setChatId(chatId);

            if (messageData.containsKey("senderId")) {
                Object senderIdObj = messageData.get("senderId");
                if (senderIdObj instanceof Number) {
                    wsMessage.setUserId(((Number) senderIdObj).longValue());
                    wsMessage.setSenderId(((Number) senderIdObj).longValue());
                } else if (senderIdObj instanceof String) {
                    try {
                        Long senderId = Long.parseLong((String) senderIdObj);
                        wsMessage.setUserId(senderId);
                        wsMessage.setSenderId(senderId);
                    } catch (NumberFormatException e) {
                        log.warn("[KAFKA] senderId as String is not a number: {}", senderIdObj);
                    }
                }
            }
            if (messageData.containsKey("senderUsername")) {
                wsMessage.setUsername((String) messageData.get("senderUsername"));
                wsMessage.setSenderUsername((String) messageData.get("senderUsername"));
            }

            if (messageType == MessageType.MESSAGE_READ) {
                if (messageData.containsKey("messageId")) {
                    Object msgIdObj = messageData.get("messageId");
                    if (msgIdObj instanceof Number) {
                        wsMessage.setMessageId(((Number) msgIdObj).longValue());
                    } else if (msgIdObj instanceof String) {
                        try {
                            wsMessage.setMessageId(Long.parseLong((String) msgIdObj));
                        } catch (NumberFormatException e) {
                            log.warn("[KAFKA] messageId as String is not a number: {}", msgIdObj);
                        }
                    }
                }
                if (messageData.containsKey("readerId")) {
                    Object readerIdObj = messageData.get("readerId");
                    if (readerIdObj instanceof Number) {
                        wsMessage.setReaderId(((Number) readerIdObj).longValue());
                    } else if (readerIdObj instanceof String) {
                        try {
                            wsMessage.setReaderId(Long.parseLong((String) readerIdObj));
                        } catch (NumberFormatException e) {
                            log.warn("[KAFKA] readerId as String is not a number: {}", readerIdObj);
                        }
                    }
                }
                if (messageData.containsKey("readerUsername")) {
                    wsMessage.setReaderUsername((String) messageData.get("readerUsername"));
                }
                log.info("[KAFKA] MESSAGE_READ details: messageId={}, readerId={}, readerUsername={}, senderId={}",
                    messageData.get("messageId"), messageData.get("readerId"),
                    messageData.get("readerUsername"), messageData.get("senderId"));
            }

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
                Object fileSizeObj = messageData.get("fileSize");
                if (fileSizeObj instanceof Number) {
                    wsMessage.setFileSize(((Number) fileSizeObj).longValue());
                } else if (fileSizeObj instanceof String) {
                    try {
                        wsMessage.setFileSize(Long.parseLong((String) fileSizeObj));
                    } catch (NumberFormatException e) {
                        log.warn("[KAFKA] fileSize as String is not a number: {}", fileSizeObj);
                    }
                }
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

            if (messageData.containsKey("id")) {
                Object idObj = messageData.get("id");
                if (idObj instanceof Number) {
                    wsMessage.setId(((Number) idObj).longValue());
                    log.info("[KAFKA] Message ID from 'id' field: {}", idObj);
                } else if (idObj instanceof String) {
                    try {
                        wsMessage.setId(Long.parseLong((String) idObj));
                        log.info("[KAFKA] Message ID from 'id' field (string): {}", idObj);
                    } catch (NumberFormatException e) {
                        log.warn("[KAFKA] id as String is not a number: {}", idObj);
                    }
                }
            } else if (messageData.containsKey("messageId")) {
                Object msgIdObj = messageData.get("messageId");
                if (msgIdObj instanceof Number) {
                    wsMessage.setId(((Number) msgIdObj).longValue());
                    log.info("[KAFKA] Message ID from 'messageId' field: {}", msgIdObj);
                } else if (msgIdObj instanceof String) {
                    try {
                        wsMessage.setId(Long.parseLong((String) msgIdObj));
                        log.info("[KAFKA] Message ID from 'messageId' field (string): {}", msgIdObj);
                    } catch (NumberFormatException e) {
                        log.warn("[KAFKA] messageId as String is not a number: {}", msgIdObj);
                    }
                }
            } else {
                log.warn("⚠[KAFKA] No ID found in message data! Keys: {}", messageData.keySet());
            }

            log.info("[KAFKA] Processing message for chat {} from user {} (ID: {}): '{}' [Type: {}, HasFile: {}, MessageID: {}]",
                chatId, wsMessage.getUsername(), wsMessage.getUserId(), wsMessage.getContent(),
                wsMessage.getMessageType(), wsMessage.getFileUrl() != null, wsMessage.getId());

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
                            String channelId = (channel != null && channel.id() != null) ? channel.id().asShortText() : "null";
                            log.debug("[FORWARD] Sent message to channel: {}", channelId);
                        } else {
                            failureCount++;
                            String channelId = (channel != null && channel.id() != null) ? channel.id().asShortText() : "null";
                            log.warn("[FORWARD] Channel is not active: {}", channelId);
                        }
                    } catch (Exception e) {
                        failureCount++;
                        String channelId = (channel != null && channel.id() != null) ? channel.id().asShortText() : "null";
                        log.error("[FORWARD] Failed to send message to channel {}: {}", channelId, e.getMessage(), e);
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
