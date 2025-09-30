package websocket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketMessage {

    private MessageType type;
    private String content;
    private String token;
    private Long chatId;
    private Long userId;
    private String username;
    private LocalDateTime timestamp;

    // Алиас для совместимости с фронтом, который возможно ожидает поле messageType
    @JsonProperty("messageType")
    public MessageType getMessageType() {
        return type;
    }

    @JsonProperty("messageType")
    public void setMessageType(MessageType mt) {
        // Если фронт присылает messageType — синхронизируем с основным полем type
        this.type = mt;
    }

    // Конструктор для быстрого создания сообщений
    public WebSocketMessage(MessageType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // Конструктор для чат сообщений
    public WebSocketMessage(MessageType type, String content, Long chatId, Long userId, String username) {
        this.type = type;
        this.content = content;
        this.chatId = chatId;
        this.userId = userId;
        this.username = username;
        this.timestamp = LocalDateTime.now();
    }
}
