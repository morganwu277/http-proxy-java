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
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.listener.NitmProxyListener;
import ai.safekids.httpproxy.testing.EmbeddedChannelAssert;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.event.HttpEvent;
import com.google.common.collect.ImmutableList;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static ai.safekids.httpproxy.http.HttpUtil.*;
import static ai.safekids.httpproxy.listener.NitmProxyListenerProvider.*;
import static com.google.common.net.HttpHeaders.*;
import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Http1EventHandlerTest {
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
        Http1EventHandler handler = new Http1EventHandler(context);
        channel = new EmbeddedChannel(handler);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

//    @Test
    public void shouldLogWithFullResponse() {
        //when(listener.onHttp1Request(any(), any(), any())).thenReturn(Optional.empty());
        when(listener.onHttp1Response(any(), any())).thenAnswer(invocation -> {
            HttpObject httpObject = (HttpObject) invocation.getArguments()[1];
            return ImmutableList.of(httpObject);
        });

        assertTrue(channel.writeInbound(defaultRequest()));
        assertTrue(channel.writeOutbound(defaultResponse("Hello Nitmproxy")));

        ArgumentCaptor<HttpEvent> captor = ArgumentCaptor.forClass(HttpEvent.class);
        verify(listener).onHttpEvent(captor.capture());
        HttpEvent event = captor.getValue();
        assertEquals(new Address("localhost", 8080), event.getClient());
        assertThat(event.getServer()).isNull();
        assertEquals(GET, event.getMethod());
        assertEquals(HTTP_1_1, event.getVersion());
        assertEquals("localhost", event.getHost());
        assertEquals("/", event.getPath());
        assertEquals(0, event.getRequestBodySize());
        assertThat(event.getRequestTime()).isCloseTo(currentTimeMillis(), Offset.offset(100L));
        assertEquals(OK, event.getStatus());
        assertEquals(TEXT_PLAIN.toString(), event.getContentType());
        assertThat(event.getResponseTime()).isGreaterThanOrEqualTo(0);
        assertEquals(15, event.getResponseBodySize());
    }

//    @Test
    public void shouldLogWithResponseAndContent() {
//        when(listener.onHttp1Request(any(), any(), any())).thenReturn(Optional.empty());
        when(listener.onHttp1Response(any(), any())).thenAnswer(invocation -> {
            HttpObject httpObject = (HttpObject) invocation.getArguments()[1];
            return ImmutableList.of(httpObject);
        });

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers()
                .add(CONTENT_TYPE, "text/plain")
                .add(CONTENT_LENGTH, 15);
        assertTrue(channel.writeInbound(defaultRequest()));
        assertTrue(channel.writeOutbound(response,
                                         new DefaultHttpContent(copiedBuffer("Hello ".getBytes(UTF_8))),
                                         new DefaultLastHttpContent(copiedBuffer("Nitmproxy".getBytes(UTF_8)))));

        ArgumentCaptor<HttpEvent> captor = ArgumentCaptor.forClass(HttpEvent.class);
        verify(listener).onHttpEvent(captor.capture());
        HttpEvent event = captor.getValue();
        assertEquals(new Address("localhost", 8080), event.getClient());
        assertThat(event.getServer()).isNull();
        assertEquals(GET, event.getMethod());
        assertEquals(HTTP_1_1, event.getVersion());
        assertEquals("localhost", event.getHost());
        assertEquals("/", event.getPath());
        assertEquals(0, event.getRequestBodySize());
        assertThat(event.getRequestTime()).isCloseTo(currentTimeMillis(), Offset.offset(100L));
        assertEquals(OK, event.getStatus());
        assertEquals(TEXT_PLAIN.toString(), event.getContentType());
        assertThat(event.getResponseTime()).isGreaterThanOrEqualTo(0);
        assertEquals(15, event.getResponseBodySize());
    }

//    @Test
    public void shouldInterceptWithResponse() {
//        when(listener.onHttp1Request(any(), any(), any())).thenReturn(Optional.of(defaultResponse("Hello Nitmproxy")));

        assertFalse(channel.writeInbound(defaultRequest()));
        EmbeddedChannelAssert.assertChannel(channel)
                             .hasOutboundMessage()
                             .hasResponse()
                             .isEqualTo(defaultResponse("Hello Nitmproxy"));

        ArgumentCaptor<HttpEvent> captor = ArgumentCaptor.forClass(HttpEvent.class);
        verify(listener).onHttpEvent(captor.capture());
        HttpEvent event = captor.getValue();
        assertEquals(new Address("localhost", 8080), event.getClient());
        assertThat(event.getServer()).isNull();
        assertEquals(GET, event.getMethod());
        assertEquals(HTTP_1_1, event.getVersion());
        assertEquals("localhost", event.getHost());
        assertEquals("/", event.getPath());
        assertEquals(0, event.getRequestBodySize());
        assertThat(event.getRequestTime()).isCloseTo(currentTimeMillis(), Offset.offset(200L));
        assertEquals(OK, event.getStatus());
        assertEquals(TEXT_PLAIN.toString(), event.getContentType());
        assertThat(event.getResponseTime()).isGreaterThanOrEqualTo(0);
        assertEquals(15, event.getResponseBodySize());
    }

//    @Test
    public void shouldPipeRequests() {
        when(listener.onHttp1Response(any(), any())).thenAnswer(invocation -> {
            HttpObject httpObject = (HttpObject) invocation.getArguments()[1];
            return ImmutableList.of(httpObject);
        });

        assertTrue(channel.writeInbound(request(HTTP_1_1, GET, "localhost", "/first")));
        assertTrue(channel.writeInbound(request(HTTP_1_1, GET, "localhost", "/second")));
        assertTrue(channel.writeOutbound(defaultResponse("First Response")));
        assertTrue(channel.writeOutbound(defaultResponse("Second Response")));

        ArgumentCaptor<HttpEvent> captor = ArgumentCaptor.forClass(HttpEvent.class);
        verify(listener, times(2)).onHttpEvent(captor.capture());

        assertThat(captor.getAllValues()).hasSize(2);
        assertEquals("/first", captor.getAllValues().get(0).getPath());
        assertEquals("/second", captor.getAllValues().get(1).getPath());
    }

}
