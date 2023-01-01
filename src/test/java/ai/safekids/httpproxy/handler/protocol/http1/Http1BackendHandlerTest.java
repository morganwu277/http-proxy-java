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
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.HandlerProvider;
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.testing.EmbeddedChannelAssert;
import ai.safekids.httpproxy.NitmProxyConfig;
import ai.safekids.httpproxy.listener.NitmProxyListenerProvider;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static ai.safekids.httpproxy.http.HttpUtil.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Http1BackendHandlerTest {
    private Http1BackendHandler handler;

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        HandlerProvider provider = mock(HandlerProvider.class);
        when(provider.wsBackendHandler()).thenReturn(new ChannelHandlerAdapter() {});
        when(provider.tailBackendHandler()).thenReturn(new ChannelHandlerAdapter() {});

        NitmProxyMaster master = mock(NitmProxyMaster.class);
        when(master.config()).thenReturn(new NitmProxyConfig());
        when(master.provider(any())).thenReturn(provider);
        when(master.listenerProvider()).thenReturn(NitmProxyListenerProvider.empty());

        channel = new EmbeddedChannel();

        ConnectionContext context = new ConnectionContext(master)
                .withClientAddr(new Address("localhost", 8080))
                .withClientChannel(channel);
        handler = new Http1BackendHandler(context);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldHandleRequestAndResponse() {
        channel.pipeline().addLast(handler);

        assertTrue(channel.writeOutbound(defaultRequest()));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasOutboundMessage()
                             .hasByteBuf()
                             .hasContent("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")
                             .release();

        assertTrue(channel.writeInbound(defaultResponse("test")));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasInboundMessage()
                             .hasResponse()
                             .release();
    }

    @Test
    public void shouldHandleRequestsAndResponses() {
        channel.pipeline().addLast(handler);

        // First request
        assertTrue(channel.writeOutbound(defaultRequest()));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasOutboundMessage()
                             .hasByteBuf()
                             .hasContent("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")
                             .release();

        // First response
        assertTrue(channel.writeInbound(defaultResponse("test")));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasInboundMessage()
                             .hasResponse()
                             .release();

        // Second request
        assertTrue(channel.writeOutbound(defaultRequest()));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasOutboundMessage()
                             .hasByteBuf()
                             .hasContent("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")
                             .release();

        // Second response
        assertTrue(channel.writeInbound(defaultResponse("test")));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasInboundMessage()
                             .hasResponse()
                             .release();
    }
}
