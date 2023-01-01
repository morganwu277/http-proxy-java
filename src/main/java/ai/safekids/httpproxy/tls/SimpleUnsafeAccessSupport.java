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
import ai.safekids.httpproxy.listener.NitmProxyListener;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.handler.protocol.http2.Http2FramesWrapper;
import com.google.common.io.Resources;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.io.Resources.*;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static java.lang.String.*;
import static java.nio.charset.StandardCharsets.*;

public class SimpleUnsafeAccessSupport implements UnsafeAccessSupport {

    private static final String ACCEPT_MAGIC = ";nitmproxy-unsafe=accept";
    private static final String DENY_MAGIC = ";nitmproxy-unsafe=deny";

    private final ConcurrentMap<Address, UnsafeAccess> accepted;
    private final String askTemplate;
    private final Interceptor interceptor;

    @SuppressWarnings("UnstableApiUsage")
    public SimpleUnsafeAccessSupport() throws IOException {
        this(Resources.toString(getResource("html-templates/ask-unsafe-access.html"), UTF_8));
    }

    public SimpleUnsafeAccessSupport(String askTemplate) {
        this.accepted = new ConcurrentHashMap<>();
        this.askTemplate = askTemplate;
        this.interceptor = new Interceptor();
    }

    @Override
    public UnsafeAccess checkUnsafeAccess(ConnectionContext context, X509Certificate[] chain,
                                          CertificateException cause) {
        accepted.putIfAbsent(context.getServerAddr(), UnsafeAccess.ASK);
        return accepted.get(context.getServerAddr());
    }

    @Override
    public TrustManagerFactory create(TrustManagerFactory delegate, ConnectionContext context) {
        return UnsafeAccessSupportTrustManagerFactory.create(delegate, this, context);
    }

    public NitmProxyListener getInterceptor() {
        return interceptor;
    }

    class Interceptor implements NitmProxyListener {
        @Override
        public Future<Optional<FullHttpResponse>> onHttp1Request(ChannelHandlerContext ctx,
                                                                 ConnectionContext connectionContext,
                                                                 FullHttpRequest request) {
            Promise<Optional<FullHttpResponse>> promise = ctx.executor().newPromise();
            if (connectionContext.getServerAddr() == null || !accepted.containsKey(connectionContext.getServerAddr())) {
                return promise.setSuccess(Optional.empty());
            }
            switch (accepted.get(connectionContext.getServerAddr())) {
                case ASK:
                    return promise.setSuccess(handleAskHttp1(connectionContext, request));
                case DENY:
                    return promise.setSuccess(Optional.of(createDenyResponse()));
                //case HttpHeaderNames.ACCEPT:
                default:
                    return promise.setSuccess(Optional.empty());
            }
        }

        @Override
        public Future<Optional<Http2FramesWrapper>> onHttp2Request(ChannelHandlerContext ctx,
                                                                   ConnectionContext context,
                                                                   Http2FramesWrapper request) {
            Promise<Optional<Http2FramesWrapper>> promise = ctx.executor().newPromise();

            if (context.getServerAddr() == null || !accepted.containsKey(context.getServerAddr())) {
                return promise.setSuccess(Optional.empty());
            }
            switch (accepted.get(context.getServerAddr())) {
                case ASK:
                    return promise.setSuccess(handleAskHttp2(context, request));
                case DENY:
                    return promise.setSuccess(Optional.of(Http2FramesWrapper
                                                              .builder(request.getStreamId())
                                                              .response(createDenyResponse())
                                                              .build()));
                //case HttpHeaderNames.ACCEPT:
                default:
                    return promise.setSuccess(Optional.empty());
            }
        }

        private Optional<FullHttpResponse> handleAskHttp1(ConnectionContext context, FullHttpRequest request) {
            if (request.uri().endsWith(ACCEPT_MAGIC)) {
                request.setUri(request.uri().replace(ACCEPT_MAGIC, ""));
                accepted.put(context.getServerAddr(), UnsafeAccess.ACCEPT);
                return Optional.empty();
            }
            if (request.uri().endsWith(DENY_MAGIC)) {
                accepted.put(context.getServerAddr(), UnsafeAccess.DENY);
                return Optional.of(createDenyResponse());
            }
            return Optional.of(createAskResponse(request.uri()));
        }

        private Optional<Http2FramesWrapper> handleAskHttp2(
            ConnectionContext connectionContext,
            Http2FramesWrapper request) {
            String uri = request.getHeaders().headers().path().toString();
            if (uri.endsWith(ACCEPT_MAGIC)) {
                request.getHeaders().headers().path(uri.replace(ACCEPT_MAGIC, ""));
                accepted.put(connectionContext.getServerAddr(), UnsafeAccess.ACCEPT);
                return Optional.empty();
            }
            if (uri.endsWith(DENY_MAGIC)) {
                accepted.put(connectionContext.getServerAddr(), UnsafeAccess.DENY);
                return Optional.of(Http2FramesWrapper
                                       .builder(request.getStreamId())
                                       .response(createDenyResponse())
                                       .build());
            }
            return Optional.of(Http2FramesWrapper
                                   .builder(request.getStreamId())
                                   .response(createAskResponse(request.getHeaders().headers().path().toString()))
                                   .build());
        }

        private FullHttpResponse createDenyResponse() {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
        }

        private FullHttpResponse createAskResponse(String uri) {
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.content().writeCharSequence(format(askTemplate, uri + ACCEPT_MAGIC, uri + DENY_MAGIC), UTF_8);
            response.headers().set(CONTENT_TYPE, TEXT_HTML);
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            return response;
        }
    }
}
