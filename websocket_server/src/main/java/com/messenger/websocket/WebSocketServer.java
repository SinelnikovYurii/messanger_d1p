package websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import websocket.service.JwtAuthService;
import websocket.service.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketServer {

    private final int port;
    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SessionManager sessionManager;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannel;

    public WebSocketServer(int port, JwtAuthService jwtAuthService, ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate, SessionManager sessionManager) {
        this.port = port;
        this.jwtAuthService = jwtAuthService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.sessionManager = sessionManager;

        log.info("🔧 [WEBSOCKET] WebSocket server initialized with Kafka integration");
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new WebSocketServerInitializer(jwtAuthService, objectMapper, kafkaTemplate, sessionManager));

            serverChannel = bootstrap.bind(port).sync();
            log.info("✅ [WEBSOCKET] WebSocket server started on port {} with Kafka integration", port);

            serverChannel.channel().closeFuture().sync();
        } finally {
            stop();
        }
    }

    public void stop() {
        log.info("Stopping WebSocket server...");

        if (serverChannel != null) {
            serverChannel.channel().close();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        log.info("WebSocket server stopped");
    }
}