package ai.safekids.httpproxy.handler.protocol.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpVersion.*;
import static org.junit.Assert.*;

public class WebSocketTestUtils {
    static final String PERMESSAGE_DEFLATE_EXTENSION_RESPONSE = "permessage-deflate";
    static final String PERMESSAGE_DEFLATE_EXTENSION_REQUEST = "permessage-deflate; client_max_window_bits";

    public static ByteBuf textFrameAsBytes(String message) {
        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        return frameToBytes(frame);
    }

    public static ByteBuf binaryFrameAsBytes(String message, int rsv) {
        BinaryWebSocketFrame frame =
            new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(message.getBytes()));
        return frameToBytes(frame);
    }

    public static ByteBuf frameToBytes(WebSocketFrame frame) {
        EmbeddedChannel testChannel = new EmbeddedChannel(new WebSocket13FrameEncoder(false));
        assertTrue(testChannel.writeOutbound(frame));
        assertTrue(testChannel.finish());
        ByteBuf encoded = testChannel.readOutbound();
        return (ByteBuf) encoded;
    }

    public static ByteBuf compressFrameToBytes(WebSocketFrame frame, boolean inbound) {
        EmbeddedChannel ch =
            new EmbeddedChannel(new WebSocket13FrameEncoder(false), new WebSocketServerCompressionHandler());

        doHandshakeCompressed(ch, inbound);

        assertTrue(ch.writeOutbound(frame));
        ByteBuf resp = ch.readOutbound();
        assertNotNull(resp);
        return resp;
    }

    @SuppressWarnings("unchecked")
    public static <T extends WebSocketFrame> T compressFrame(T frame, boolean inbound) {
        WebSocketFrame resp;

        ChannelHandler handler =
            (inbound)? new WebSocketServerCompressionHandler() : WebSocketClientCompressionHandler.INSTANCE;

        EmbeddedChannel ch = new EmbeddedChannel(handler);
        doHandshakeCompressed(ch, inbound);

        if (inbound) {
            assertTrue(ch.writeOutbound(frame));
            resp = ch.readOutbound();
        } else {
            assertTrue(ch.writeInbound(frame));
            resp = ch.readInbound();
        }

        assertNotNull(resp);
        return (T) resp;
    }

    public static void doHandshake(EmbeddedChannel ch, boolean inbound) {
        doHandshake(ch, inbound, null, null);
    }

    public static void doHandshakeCompressed(EmbeddedChannel ch, boolean inbound) {
        doHandshake(ch, inbound, PERMESSAGE_DEFLATE_EXTENSION_REQUEST,
                    PERMESSAGE_DEFLATE_EXTENSION_RESPONSE);
    }

    public static void doHandshake(EmbeddedChannel ch,
                                   boolean inbound,
                                   String requestExtension,
                                   String responseExtension) {
        HttpRequest req = newUpgradeRequest(requestExtension);
        HttpResponse res = newUpgradeResponse(responseExtension);
        doHandshakeDirect(ch, inbound, req, res);
    }

    public static void doHandshakeDirect(EmbeddedChannel ch, boolean inbound, HttpRequest req, HttpResponse res) {
        HttpRequest a;
        HttpResponse b;

        if (inbound) {
            assertTrue(ch.writeInbound(req));
            assertTrue(ch.writeOutbound(res));
            a = ch.readInbound();
            b = ch.readOutbound();
        } else {
            assertTrue(ch.writeOutbound(req));
            assertTrue(ch.writeInbound(res));
            a = ch.readOutbound();
            b = ch.readInbound();
        }

        if (a instanceof FullHttpRequest) {
            ((FullHttpRequest) a).release();
        }
        if (b instanceof FullHttpResponse) {
            ((FullHttpResponse) b).release();
        }
        assertNotNull(a);
        assertNotNull(b);
    }

    public static HttpRequest createRequest(String input) {
        ByteBuf buff = Unpooled.wrappedBuffer(input.getBytes(StandardCharsets.US_ASCII));
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(ch.writeInbound(buff));
        HttpRequest resp = (HttpRequest) ch.readInbound();
        assertNotNull(resp);
        ch.finishAndReleaseAll();
        return resp;
    }

    public static HttpResponse createResponse(String input) {
        ByteBuf buff = Unpooled.wrappedBuffer(input.getBytes(StandardCharsets.US_ASCII));
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(ch.writeInbound(buff));
        HttpResponse resp = (HttpResponse) ch.readInbound();
        assertNotNull(resp);
        ch.finishAndReleaseAll();
        return resp;
    }

    public static FullHttpRequest newUpgradeRequest(String extension) {
        FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/chat");
        request.headers().set(HttpHeaderNames.HOST, "server.example.com");
        request.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        request.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://example.com");
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
        if (extension != null) {
            request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, extension);
        }
        return request;
    }

    public static FullHttpResponse newUpgradeResponse(String extension) {
        FullHttpResponse response =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        response.headers().set(HttpHeaderNames.HOST, "server.example.com");
        response.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET.toString().toLowerCase());
        response.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        response.headers().set(HttpHeaderNames.ORIGIN, "http://example.com");
        if (extension != null) {
            response.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, extension);
        }
        return response;
    }
}
