package websocket.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class HttpRequestHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<String> TOKEN_ATTRIBUTE = AttributeKey.valueOf("token");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;

            log.info("HttpRequestHandler: Processing HTTP request from {}", ctx.channel().remoteAddress());
            log.info("HttpRequestHandler: Request method: {}, URI: {}", request.method(), request.uri());

            // Извлекаем токен из query параметров URL
            String uri = request.uri();
            QueryStringDecoder decoder = new QueryStringDecoder(uri);
            log.info("HttpRequestHandler: Parsed URI path: {}", decoder.path());
            log.info("HttpRequestHandler: Query parameters: {}", decoder.parameters());

            List<String> tokenParams = decoder.parameters().get("token");
            if (tokenParams != null && !tokenParams.isEmpty()) {
                String token = tokenParams.get(0);
                log.info("HttpRequestHandler: Extracted token from URL: {}...", token.substring(0, Math.min(token.length(), 20)));

                // Сохраняем токен в атрибутах канала для использования в WebSocketFrameHandler
                ctx.channel().attr(TOKEN_ATTRIBUTE).set(token);
                log.info("HttpRequestHandler: Token stored in channel attributes for connection: {}", ctx.channel().id());
            } else {
                log.warn("HttpRequestHandler: No token found in WebSocket connection request URI: {}", uri);
                log.warn("HttpRequestHandler: Available query parameters: {}", decoder.parameters().keySet());
            }
        }

        // Всегда передаем сообщение дальше
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("HttpRequestHandler: Error for connection {}: {}", ctx.channel().id(), cause.getMessage(), cause);
        super.exceptionCaught(ctx, cause);
    }
}
