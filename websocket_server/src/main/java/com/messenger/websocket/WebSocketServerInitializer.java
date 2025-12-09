package com.messenger.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;

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
import com.messenger.websocket.handler.WebSocketFrameHandler;
import com.messenger.websocket.handler.HttpRequestHandler;
import com.messenger.websocket.service.JwtAuthService;
import com.messenger.websocket.service.SessionManager;

public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SessionManager sessionManager;

    public WebSocketServerInitializer(JwtAuthService jwtAuthService, ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate, SessionManager sessionManager) {
        this.jwtAuthService = jwtAuthService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.sessionManager = sessionManager; // Используем переданный Spring bean
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

        WebSocketServerProtocolHandler wsHandler = new WebSocketServerProtocolHandler("/ws/chat", null, true, 65536, false, true);
        pipeline.addLast(wsHandler);


        pipeline.addLast(new WebSocketFrameHandler(jwtAuthService, objectMapper, sessionManager, kafkaTemplate));
    }
}