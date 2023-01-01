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

package ai.safekids.httpproxy.handler.protocol.http1;

import ai.safekids.httpproxy.Address;
import ai.safekids.httpproxy.HandlerProvider;
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.testing.EmbeddedChannelAssert;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.NitmProxyConfig;
import ai.safekids.httpproxy.listener.NitmProxyListenerProvider;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static ai.safekids.httpproxy.http.HttpUtil.*;
import static com.google.common.net.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Http1FrontendHandlerTest {
    private NitmProxyMaster master;

    private EmbeddedChannel inboundChannel;

    private EmbeddedChannel outboundChannel;

    @Before
    public void setUp() {
        master = mock(NitmProxyMaster.class);
        HandlerProvider provider = mock(HandlerProvider.class);
        when(master.config()).thenReturn(new NitmProxyConfig());
        when(master.provider(any())).thenReturn(provider);
        when(master.listenerProvider()).thenReturn(NitmProxyListenerProvider.empty());
        when(provider.http1EventHandler()).thenReturn(new ChannelHandlerAdapter() {});
        when(provider.tlsFrontendHandler()).thenReturn(new ChannelHandlerAdapter() {});
        when(provider.wsFrontendHandler()).thenReturn(new ChannelHandlerAdapter() {});

        inboundChannel = new EmbeddedChannel();
    }

    @After
    public void tearDown() {
        inboundChannel.finishAndReleaseAll();

        if (outboundChannel != null) {
            outboundChannel.finishAndReleaseAll();
        }
    }

    @Test
    public void shouldTunnelRequest() {
        Http1FrontendHandler handler = tunneledHandler();
        inboundChannel.pipeline().addLast(handler);

        assertTrue(inboundChannel.writeInbound(toBytes(defaultRequest())));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost")
                             .hasContent("")
                             .release();
    }

    @Test
    public void shouldTunnelRequests() {
        Http1FrontendHandler handler = tunneledHandler();
        inboundChannel.pipeline().addLast(handler);

        assertTrue(inboundChannel.writeInbound(toBytes(defaultRequest())));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost")
                             .hasContent("")
                             .release();

        assertTrue(inboundChannel.writeInbound(toBytes(defaultRequest())));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost")
                             .hasContent("")
                             .release();
    }

    @Test
    public void shouldHandleHttpProxyRequest() {
        Http1FrontendHandler handler = httpProxyHandler(true);
        inboundChannel.pipeline().addLast(handler);

        ByteBuf requestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:9000", "http://localhost:9000/"));
        assertTrue(inboundChannel.writeInbound(requestBytes));

        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost:9000")
                             .hasContent("")
                             .release();
    }

    @Test
    public void shouldHandleHttpProxyRequests() {
        Http1FrontendHandler handler = httpProxyHandler(true);
        inboundChannel.pipeline().addLast(handler);

        ByteBuf requestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:9000", "http://localhost:9000/"));
        assertTrue(inboundChannel.writeInbound(requestBytes.copy()));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost:9000")
                             .hasContent("")
                             .release();

        // Second request
        assertTrue(inboundChannel.writeInbound(requestBytes));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost:9000")
                             .hasContent("")
                             .release();
    }

    @Test
    public void shouldHandleHttpProxyCreateNewConnection() {
        Http1FrontendHandler handler = httpProxyHandler(true);
        inboundChannel.pipeline().addLast(handler);

        ByteBuf firstRequestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:8000", "http://localhost:8000/"));

        assertTrue(inboundChannel.writeInbound(firstRequestBytes));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost:8000")
                             .hasContent("")
                             .release();

        EmbeddedChannel firstOutboundChannel = outboundChannel;

        // Second request
        ByteBuf secondRequestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:9000", "http://localhost:9000/"));
        assertTrue(inboundChannel.writeInbound(secondRequestBytes));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .hasMethod(GET)
                             .hasUrl("/")
                             .hasHeader(HOST, "localhost:9000")
                             .hasContent("")
                             .release();

        assertNotSame(firstOutboundChannel, outboundChannel);
        assertFalse(firstOutboundChannel.isActive());
    }

    @Test
    public void shouldClosedWhenHttpProxyDestinationNotAvailable() {
        Http1FrontendHandler handler = httpProxyHandler(false);
        inboundChannel.pipeline().addLast(handler);

        ByteBuf requestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:8000", "http://localhost:8000/"));
        assertFalse(inboundChannel.writeInbound(requestBytes));

        assertFalse(inboundChannel.isActive());
    }

    @Test
    public void shouldCreateNewOutboundWhenOldIsInactive() {
        Http1FrontendHandler handler = httpProxyHandler(true);
        inboundChannel.pipeline().addLast(handler);

        ByteBuf firstRequestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:8000", "http://localhost:8000/"));
        assertTrue(inboundChannel.writeInbound(firstRequestBytes));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .release();

        EmbeddedChannel firstOutboundChannel = outboundChannel;
        outboundChannel.close();

        // Second request
        ByteBuf secondRequestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, GET, "localhost:8000", "http://localhost:8000/"));
        assertTrue(inboundChannel.writeInbound(secondRequestBytes));
        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasInboundMessage()
                             .hasRequest()
                             .release();

        assertNotSame(firstOutboundChannel, outboundChannel);
    }

    @Test
    public void shouldHandleConnect() {
        Http1FrontendHandler handler = httpProxyHandler(true);
        inboundChannel.pipeline().addLast(handler);

        ByteBuf requestBytes = toBytes(request(
                HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "localhost:8000", "http://localhost:8000/"));
        assertFalse(inboundChannel.writeInbound(requestBytes));

        assertNotNull(outboundChannel);
        assertTrue(outboundChannel.isActive());

        EmbeddedChannelAssert.assertChannel(inboundChannel)
                             .hasOutboundMessage()
                             .hasByteBuf()
                             .hasContent("HTTP/1.1 200 OK\r\n\r\n")
                             .release();
    }

    private Http1FrontendHandler httpProxyHandler(boolean outboundAvailable) {
        if (outboundAvailable) {
            when(master.connect(any(), any(), any())).then(
                    invocationOnMock ->  {
                        outboundChannel = new EmbeddedChannel((ChannelHandler) invocationOnMock.getArguments()[2]);
                        return outboundChannel.newSucceededFuture();
                    });
        } else {
            when(master.connect(any(), any(), any())).then(
                    invocationOnMock ->  inboundChannel.newPromise().setFailure(new Exception()));
        }
        return new Http1FrontendHandler(master, createConnectionContext());
    }

    private Http1FrontendHandler tunneledHandler() {
        outboundChannel = new EmbeddedChannel();
        return new Http1FrontendHandler(master, createConnectionContext());
    }

    private ConnectionContext createConnectionContext() {
        ConnectionContext context = new ConnectionContext(master)
                .withClientAddr(new Address("localhost", 8080))
                .withClientChannel(inboundChannel);
        if (outboundChannel != null) {
            context.withServerAddr(new Address("localhost", 8080))
                    .withServerChannel(outboundChannel);
        }
        return context;
    }
}
