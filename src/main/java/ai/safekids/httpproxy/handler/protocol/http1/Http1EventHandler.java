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

import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.http.HttpHeadersUtil;
import ai.safekids.httpproxy.listener.NitmProxyListener;
import ai.safekids.httpproxy.event.HttpEvent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.*;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.util.ReferenceCountUtil.*;
import static java.lang.System.*;

public class Http1EventHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http1EventHandler.class);

    private NitmProxyListener listener;
    private ConnectionContext connectionContext;

    private long requestTime;
    private Queue<FullHttpRequest> requests;
    private HttpResponse response;
    private AtomicLong responseBytes;

    /**
     * Create new instance of http1 event handler.
     *
     * @param connectionContext the connection context
     */
    public Http1EventHandler(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.listener = connectionContext.listener();
        this.requests = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
        throws Exception {
        if (!(msg instanceof HttpObject)) {
            ctx.write(msg, promise);
            return;
        }
        List<HttpObject> output = listener.onHttp1Response(connectionContext, (HttpObject) msg);
        for (HttpObject httpObject : output) {
            if (httpObject instanceof HttpResponse) {
                checkState(!requests.isEmpty(), "request is empty");
                checkState(response == null, "response is not null");
                responseBytes = new AtomicLong();
                response = retain((HttpResponse) httpObject);
            }
            if (httpObject instanceof HttpContent) {
                checkState(responseBytes != null, "responseBytes is null");
                HttpContent httpContent = (HttpContent) msg;
                responseBytes.addAndGet(httpContent.content().readableBytes());
            }
            if (httpObject instanceof LastHttpContent) {
                checkState(!requests.isEmpty(), "request is empty");
                checkState(response != null, "response is null");
                FullHttpRequest request = requests.poll();
                long responseTime = currentTimeMillis();
                HttpEvent httpEvent = HttpEvent.builder(connectionContext)
                                               .method(request.method())
                                               .version(request.protocolVersion())
                                               .host(request.headers().get(HOST))
                                               .path(request.uri())
                                               .requestBodySize(request.content().readableBytes())
                                               .requestTime(requestTime)
                                               .status(response.status())
                                               .contentType(HttpHeadersUtil.getContentType(response.headers()))
                                               .responseTime(responseTime)
                                               .responseBodySize(responseBytes.get())
                                               .build();
                try {
                    listener.onHttpEvent(httpEvent);
                } finally {
                    release(request);
                    release(response);
                    requestTime = 0;
                    response = null;
                    responseBytes = null;
                }
            }
        }

        if (output.isEmpty()) {
            ctx.write(msg, promise);
        } else if (output.size() == 1) {
            ctx.write(output.get(0), promise);
        } else {
            PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
            output.stream().map(ctx::write).forEach(combiner::add);
            combiner.finish(promise);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        /**
         * Create a new promise to receive the callback
         */
        try {
            FullHttpRequest request = (FullHttpRequest) msg;
            Future<Optional<FullHttpResponse>> responsePromise =
                listener.onHttp1Request(ctx, connectionContext, request);

            responsePromise.addListener(p -> {
                boolean isCustomResponse = responsePromise.isSuccess() && responsePromise.getNow() != null &&
                                           responsePromise.getNow().isPresent();
                if (isCustomResponse) {
                    FullHttpResponse response = responsePromise.getNow().get();
                    try {
                        sendResponse(ctx, request, response);
                    } finally {
                        request.release();
                    }
                } else {
                    this.requests.add(request.retain());
                    this.requestTime = currentTimeMillis();
                    ctx.fireChannelRead(msg);
                }
            });
        } catch (Exception e) {
            LOGGER.debug("onHttp1Request error", e);
            throw e;
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        HttpEvent httpEvent = HttpEvent.builder(connectionContext)
                                       .method(request.method())
                                       .version(request.protocolVersion())
                                       .host(request.headers().get(HOST))
                                       .path(request.uri())
                                       .requestBodySize(request.content().readableBytes())
                                       .requestTime(currentTimeMillis())
                                       .status(response.status())
                                       .contentType(HttpHeadersUtil.getContentType(response.headers()))
                                       .responseBodySize(response.content().readableBytes())
                                       .build();
        listener.onHttpEvent(httpEvent);
        ctx.writeAndFlush(response);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        requests.forEach(FullHttpRequest::release);
        release(response);
    }
}
