package websocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import websocket.handler.WebSocketFrameHandler;
import websocket.handler.HttpRequestHandler;
import websocket.service.JwtAuthService;

public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;

    public WebSocketServerInitializer(JwtAuthService jwtAuthService, ObjectMapper objectMapper) {
        this.jwtAuthService = jwtAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        CorsConfig corsConfig = CorsConfigBuilder.forAnyOrigin()
                .allowNullOrigin()
                .allowCredentials()
                .allowedRequestHeaders("*")
                .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)
                .build();
        pipeline.addLast(new CorsHandler(corsConfig));

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new HttpRequestHandler());
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler("/chat", null, true));
        pipeline.addLast(new WebSocketFrameHandler(jwtAuthService, objectMapper));
    }
}