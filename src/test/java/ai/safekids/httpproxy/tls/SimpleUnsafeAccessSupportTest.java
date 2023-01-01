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

package ai.safekids.httpproxy.tls;

import ai.safekids.httpproxy.Address;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.tls.SimpleUnsafeAccessSupport.Interceptor;
import io.netty.handler.codec.http.FullHttpRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static ai.safekids.httpproxy.http.HttpUtil.*;
import static ai.safekids.httpproxy.tls.UnsafeAccess.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SimpleUnsafeAccessSupportTest {
    private SimpleUnsafeAccessSupport unsafeAccessSupport;
    private Interceptor interceptor;
    private ConnectionContext context;
    private FullHttpRequest request;

    @Before
    public void setUp() throws IOException {
        context = mock(ConnectionContext.class);
        when(context.getServerAddr()).thenReturn(new Address("www.google.com", 443));

        unsafeAccessSupport = new SimpleUnsafeAccessSupport();
        interceptor = (Interceptor) unsafeAccessSupport.getInterceptor();
        request = request(HTTP_1_1, GET, "www.google.com", "/");
    }

    @After
    public void tearDown() {
        request.release();
    }

    @Test
    public void shouldGetAsk() {
        assertEquals(ASK, unsafeAccessSupport.checkUnsafeAccess(context, new X509Certificate[0], new CertificateException()));
    }

    @Test
    public void shouldNotIntercept() {
        //assertThat(interceptor.onHttp1Request(null, context, request)).isEmpty();
    }

    //@Test
    public void shouldInterceptOnAsk() {
        assertEquals(ASK, unsafeAccessSupport.checkUnsafeAccess(context, new X509Certificate[0], new CertificateException()));

//        assertThat(interceptor.onHttp1Request(null, context, request))
//                .isPresent()
//                .get(asResponse())
//                .content()
//                .hasContent("<html>\n" +
//                            "<body>\n" +
//                            "  <a href=\"/;nitmproxy-unsafe=accept\">Accept</a>\n" +
//                            "  <a href=\"/;nitmproxy-unsafe=deny\">Reject</a>\n" +
//                            "</body>\n" +
//                            "</html>")
//                .release();
    }

//    @Test
    public void shouldInterceptOnAccept() {
        assertEquals(ASK, unsafeAccessSupport.checkUnsafeAccess(context, new X509Certificate[0], new CertificateException()));

        request.setUri("/;nitmproxy-unsafe=accept");
//        assertThat(interceptor.onHttp1Request(null, context, request)).isEmpty();
        assertEquals("/", request.uri());

        assertEquals(ACCEPT, unsafeAccessSupport.checkUnsafeAccess(context, new X509Certificate[0], new CertificateException()));
    }

//    @Test
    public void shouldInterceptOnDeny() {
        assertEquals(ASK, unsafeAccessSupport.checkUnsafeAccess(context, new X509Certificate[0], new CertificateException()));

        request.setUri("/;nitmproxy-unsafe=deny");
//        assertThat(interceptor.onHttp1Request(null, context, request))
//                .isPresent()
//                .get(asResponse())
//                .status(FORBIDDEN)
//                .release();
        assertEquals(DENY, unsafeAccessSupport.checkUnsafeAccess(context, new X509Certificate[0], new CertificateException()));

        request.setUri("/");
//        assertThat(interceptor.onHttp1Request(context, request))
//                .isPresent()
//                .get(asResponse())
//                .status(FORBIDDEN)
//                .release();
    }
}
