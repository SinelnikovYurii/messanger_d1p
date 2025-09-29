package websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import websocket.service.JwtAuthService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketServer {

    private final int port;
    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannel;

    public WebSocketServer(int port, JwtAuthService jwtAuthService, ObjectMapper objectMapper) {
        this.port = port;
        this.jwtAuthService = jwtAuthService;
        this.objectMapper = objectMapper;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(8192));
                            // Добавляем обработчик для извлечения токена из URL
                            pipeline.addLast(new websocket.handler.HttpRequestHandler());
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws/chat", null, true));
                            pipeline.addLast(new websocket.handler.WebSocketFrameHandler(jwtAuthService, objectMapper));
                        }
                    });

            serverChannel = bootstrap.bind(port).sync();
            log.info("WebSocket server started on port {}", port);

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