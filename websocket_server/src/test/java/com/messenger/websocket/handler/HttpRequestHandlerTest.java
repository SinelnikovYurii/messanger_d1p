package com.messenger.websocket.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import org.junit.jupiter.api.Test;
import com.messenger.websocket.handler.HttpRequestHandler;

import static org.junit.jupiter.api.Assertions.*;

public class HttpRequestHandlerTest {
    @Test
    void testTokenExtractedAndStored() {
        String uri = "/ws?token=abc123";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        assertEquals("abc123", token);
    }

    @Test
    void testNoTokenInRequest() {
        String uri = "/ws?user=1";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        assertNull(token);
    }

    @Test
    void testMessageForwarded() {
        String uri = "/ws?token=abc123";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        Object msg = channel.readInbound();
        assertInstanceOf(FullHttpRequest.class, msg);
    }

    @Test
    void testExceptionCaught() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        Exception ex = new Exception("Test error");
        assertDoesNotThrow(() -> channel.pipeline().fireExceptionCaught(ex));
    }

    @Test
    void testMultipleQueryParameters() {
        String uri = "/ws?token=xyz789&userId=123&sessionId=abc";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        assertEquals("xyz789", token);
    }

    @Test
    void testEmptyToken() {
        String uri = "/ws?token=";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        // Пустой токен должен быть сохранен как пустая строка
        assertEquals("", token);
    }

    @Test
    void testTokenWithSpecialCharacters() {
        String uri = "/ws?token=abc.123_xyz-789";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        assertEquals("abc.123_xyz-789", token);
    }

    @Test
    void testUriWithoutQueryString() {
        String uri = "/ws";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        assertNull(token);
    }

    @Test
    void testTokenCaseInsensitive() {
        String uri = "/ws?TOKEN=abc123";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(request);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        // Проверяем, чувствителен ли параметр к регистру (обычно нет)
        assertNull(token);
    }

    @Test
    void testDifferentHttpMethods() {
        String uri = "/ws?token=abc123";
        FullHttpRequest postRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestHandler());
        channel.writeInbound(postRequest);
        String token = channel.attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
        assertEquals("abc123", token);
    }
}
