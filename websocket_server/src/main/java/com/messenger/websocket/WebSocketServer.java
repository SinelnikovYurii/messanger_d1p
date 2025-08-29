package websocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import websocket.service.JwtAuthService;

@Slf4j
public class WebSocketServer {

    private final int port;
    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    public WebSocketServer(int port, JwtAuthService jwtAuthService, ObjectMapper objectMapper) {
        this.port = port;
        this.jwtAuthService = jwtAuthService;
        this.objectMapper = objectMapper;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketServerInitializer(jwtAuthService, objectMapper))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            channelFuture = b.bind(port).sync();
            log.info("Netty WebSocket server started on port {}", port);


            channelFuture.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    public void stop() {
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        shutdown();
        log.info("Netty WebSocket server stop signal sent");
    }

    private void shutdown() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}