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

package ai.safekids.httpproxy.handler.protocol.http2;

import ai.safekids.httpproxy.Address;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.event.HttpEvent;
import ai.safekids.httpproxy.listener.NitmProxyListener;
import com.google.common.collect.ImmutableList;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static ai.safekids.httpproxy.http.HttpUtil.*;
import static ai.safekids.httpproxy.listener.NitmProxyListenerProvider.*;
import static ai.safekids.httpproxy.testing.EmbeddedChannelAssert.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static java.lang.System.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Http2EventHandlerTest {

    private NitmProxyListener listener;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        listener = mock(NitmProxyListener.class);
        NitmProxyMaster master = mock(NitmProxyMaster.class);
        when(master.listenerProvider()).thenReturn(singleton(listener));

        ConnectionContext context = new ConnectionContext(master)
            .withClientAddr(new Address("localhost", 8080))
            .withClientChannel(channel);
        Http2EventHandler handler = new Http2EventHandler(context);
        channel = new EmbeddedChannel(handler);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    public Promise<Optional<Http2FramesWrapper>> promiseOf(Optional<Http2FramesWrapper> result) {
        Promise<Optional<Http2FramesWrapper>> promise = channel.eventLoop().newPromise();
        promise.setSuccess(result);
        return promise;
    }

    public Promise<Optional<Http2FramesWrapper>> emptyRequest() {
        Promise<Optional<Http2FramesWrapper>> promise = channel.eventLoop().newPromise();
        promise.setSuccess(Optional.empty());
        return promise;
    }

//    @Test
    public void shouldSendRequestAfterRequestEnded() {
//        when(listener.onHttp2Request(any(), any(), any())).thenReturn(promise);
        List<Http2FrameWrapper<?>> requestFrames = Http2FramesWrapper
            .builder(1)
            .request(textRequest(HttpVersion.HTTP_1_1, POST, "localhost", "/", "Hello nitmproxy"))
            .build()
            .getAllFrames();
        assertFalse(channel.writeInbound(requestFrames.get(0)));
        assertTrue(channel.writeInbound(requestFrames.get(1)));
        assertChannel(channel)
            .hasInboundMessage()
            .hasSize(2);
        assertThat(channel.inboundMessages().poll()).isEqualTo(requestFrames.get(0));
        assertThat(channel.inboundMessages().poll()).isEqualTo(requestFrames.get(1));
        requestFrames.forEach(ReferenceCountUtil::release);
    }

    @Test
    public void shouldLogWithFullResponse() {
        when(listener.onHttp2Request(any(), any(), any())).thenReturn(emptyRequest());
        when(listener.onHttp2Response(any(), any())).thenAnswer(invocation -> {
            Http2FrameWrapper<?> frame = (Http2FrameWrapper<?>) invocation.getArguments()[1];
            return ImmutableList.of(frame);
        });

        Http2FramesWrapper
            .builder(1)
            .request(defaultRequest())
            .build()
            .getAllFrames()
            .forEach(channel::writeInbound);
        Http2FramesWrapper
            .builder(1)
            .response(defaultResponse("Hello nitmproxy"))
            .build()
            .getAllFrames()
            .forEach(channel::writeOutbound);

        ArgumentCaptor<HttpEvent> captor = ArgumentCaptor.forClass(HttpEvent.class);
        verify(listener).onHttpEvent(captor.capture());
        HttpEvent event = captor.getValue();
        assertEquals(new Address("localhost", 8080), event.getClient());
        assertThat(event.getServer()).isNull();
        assertEquals(GET, event.getMethod());
        assertEquals(HTTP_2, event.getVersion());
        assertEquals("localhost", event.getHost());
        assertEquals("/", event.getPath());
        assertEquals(0, event.getRequestBodySize());
        assertThat(event.getRequestTime()).isCloseTo(currentTimeMillis(), Offset.offset(100L));
        assertEquals(OK, event.getStatus());
        assertEquals(TEXT_PLAIN.toString(), event.getContentType());
        assertThat(event.getResponseTime()).isGreaterThanOrEqualTo(0);
        assertEquals(15, event.getResponseBodySize());
    }

    @Test
    public void shouldInterceptWithResponse() {
        when(listener.onHttp2Request(any(), any(), any())).thenReturn(
            promiseOf(Optional.of(Http2FramesWrapper
                                      .builder(1)
                                      .response(defaultResponse("Hello nitmproxy"))
                                      .build())));
        Http2FramesWrapper
            .builder(1)
            .request(defaultRequest())
            .build()
            .getAllFrames()
            .forEach(channel::writeInbound);

        assertChannel(channel).hasOutboundMessage().hasSize(2);

        ArgumentCaptor<HttpEvent> captor = ArgumentCaptor.forClass(HttpEvent.class);
        verify(listener).onHttpEvent(captor.capture());
        HttpEvent event = captor.getValue();
        assertEquals(new Address("localhost", 8080), event.getClient());
        assertThat(event.getServer()).isNull();
        assertEquals(GET, event.getMethod());
        assertEquals(HTTP_2, event.getVersion());
        assertEquals("localhost", event.getHost());
        assertEquals("/", event.getPath());
        assertEquals(0, event.getRequestBodySize());
        assertThat(event.getRequestTime()).isCloseTo(currentTimeMillis(), Offset.offset(100L));
        assertEquals(OK, event.getStatus());
        assertEquals(TEXT_PLAIN.toString(), event.getContentType());
        assertThat(event.getResponseTime()).isGreaterThanOrEqualTo(0);
        assertEquals(15, event.getResponseBodySize());
    }
}
