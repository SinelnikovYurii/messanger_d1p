package websocket.model;

import io.netty.channel.Channel;
import lombok.Data;

import io.netty.channel.Channel;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {
    private Long userId;
    private Channel channel;
    private String username;
    private Long currentChatId;
}
