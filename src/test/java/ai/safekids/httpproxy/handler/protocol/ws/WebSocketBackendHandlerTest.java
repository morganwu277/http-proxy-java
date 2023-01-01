/*
 * Copyright 2021 - Safe Kids LLC
 *
 * The complete license agreement is available at https://safekids.ai/eula
 *
 *
 * LICENSE GRANT
 * ==============================================
 * Licensor hereby grants to you a non-exclusive and non-transferable license to use the Software and
 * related documentation (the "Documentation") solely for the intended purposes of the Software as set forth in the
 * Documentation, according to the provisions contained herein and subject to payment of applicable license fees.
 * You are not permitted to lease, rent, distribute or sublicense the Software or any rights therein.
 * You also may not install the Software on a network server, use the Software in a time-sharing arrangement or
 * in any other unauthorized manner. Further, no license is granted to you in the human readable code of the Software
 *  (source code). Except as provided below, this Agreement does not grant you any rights to patents, copyrights,
 *  trade secrets, trademarks, or any other rights in the Software and Documentation.
 *
 * NO MODIFICATION, NO REVERSE ENGINEERING
 * ===============================================
 * You agree not to, without the prior written permission of Licensor: (i); disassemble, decompile or "unlock",
 * decode or otherwise reverse translate or engineer, or attempt in any manner to reconstruct or discover any source
 * code or underlying algorithms of the Software, if provided in object code form only; (ii) use, copy,
 * modify, translate,reverse engineer, decompile, disassemble, or create derivative works of the Software and
 * any accompanying documents, or assist someone in performing such prohibited acts; or (iii) transfer, rent,
 * lease, or sub license the Software.
 *
 * NO WARRANTIES.
 * ===============================================
 * LICENSOR MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT OF THIRD PARTIES' RIGHTS.
 * THE SOFTWARE (INCLUDING SOURCE CODE) IS PROVIDED TO YOU ON AN "AS IS" BASIS. TO THE FULL EXTENT PERMITTED BY LAW,
 * THE DURATION OF STATUTORILY REQUIRED WARRANTIES, IF ANY, SHALL BE LIMITED TO THE ABOVE LIMITED WARRANTY PERIOD.
 * MOREOVER, IN NO EVENT WILL WARRANTIES PROVIDED BY LAW, IF ANY, APPLY UNLESS THEY ARE REQUIRED TO APPLY BY
 * STATUTE NOTWITHSTANDING THEIR EXCLUSION BY CONTRACT. NO DEALER, AGENT, OR EMPLOYEE OF LICENSOR IS AUTHORIZED TO
 * MAKE ANY MODIFICATIONS, EXTENSIONS, OR ADDITIONS TO THIS LIMITED WARRANTY. THE ENTIRE RISK ARISING OUT OF USE OR
 * PERFORMANCE OF THE SOFTWARE REMAINS WITH YOU.
 *
 */

package ai.safekids.httpproxy.handler.protocol.ws;

import ai.safekids.httpproxy.Address;
import ai.safekids.httpproxy.HandlerProvider;
import ai.safekids.httpproxy.handler.HeadExceptionHandler;
import ai.safekids.httpproxy.testing.EmbeddedChannelAssert;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.listener.NitmProxyListenerProvider;
import ai.safekids.httpproxy.testing.EmptyChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static ai.safekids.httpproxy.handler.protocol.ws.WebSocketTestUtils.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WebSocketBackendHandlerTest {


    ConnectionContext createContext() {
        NitmProxyMaster master = mock(NitmProxyMaster.class);
        when(master.listenerProvider()).thenReturn(NitmProxyListenerProvider.empty());

        ConnectionContext context = new ConnectionContext(master)
            .withClientAddr(new Address("localhost", 8888));
        return context;
    }

//    @Test
    @SuppressWarnings("unchecked")
    public void shouldHandshake() {
        ConnectionContext context = createContext();
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketBackendHandler(context));
        assertTrue(channel.writeOutbound(newUpgradeRequest(null)));

        EmbeddedChannelAssert.assertChannel(channel)
                             .hasOutboundMessage()
                             .hasRequest()
                             .release();

        assertTrue(channel.writeInbound(newUpgradeResponse(null)));

        EmbeddedChannelAssert.assertChannel(channel)
                             .hasInboundMessage()
                             .hasResponse()
                             .release();
        EmbeddedChannelAssert.assertChannel(channel)
                             .pipeline()
                             .hasHandlers(HeadExceptionHandler.class,
                                          WebSocketFrameLogger.class,
                                          WebSocket13FrameEncoder.class,
                                          WebSocket13FrameDecoder.class);
    }

    @Test
    public void testUncompressedTextFrame() {
        ConnectionContext context = createContext();
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketBackendHandler(context));
        doHandshake(ch, false);

        //send text frame
        String message = "hello";
        ByteBuf frame = textFrameAsBytes(message);
        assertTrue(ch.writeInbound(frame));
        TextWebSocketFrame ret = ch.readInbound();
        assertEquals(message, ret.text());
        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testCompressedBinaryFrame() {
        ConnectionContext context = createContext();
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketBackendHandler(context));

        String message = "Hello";
        ByteBuf payload = Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));

        doHandshakeCompressed(channel, false);

        //check binary frame compression
        ByteBuf frameBytes = compressFrameToBytes(new BinaryWebSocketFrame(true, 0, payload), true);
        assertTrue(channel.writeInbound(frameBytes));
        BinaryWebSocketFrame output = channel.readInbound();
        assertNotNull(output);
        assertEquals("Hello", new String(ByteBufUtil.getBytes(output.content())));
        channel.finishAndReleaseAll();
    }
}
