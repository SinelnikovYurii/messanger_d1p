package websocket.model;



import lombok.Data;
import websocket.model.MessageType;

import java.time.LocalDateTime;

@Data
public class WebSocketMessage {
    private MessageType type;
    private Object payload;
    private LocalDateTime timestamp;

    public WebSocketMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public WebSocketMessage(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
    }
}
