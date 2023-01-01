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

import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.handler.HeadExceptionHandler;
import ai.safekids.httpproxy.http.HttpHeadersUtil;
import ai.safekids.httpproxy.handler.protocol.http1.Http1FrontendHandler;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class WebSocketFrontendHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketFrontendHandler.class);

    private ConnectionContext connectionContext;

    public WebSocketFrontendHandler(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);
        ctx.pipeline().addAfter(ctx.name(), null, new WebSocketServerCompressionHandler());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerRemoved", connectionContext);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest && HttpHeadersUtil.isWebSocketUpgrade(((FullHttpRequest) msg).headers())) {
            LOGGER.debug("{} : ws upgrading {}", connectionContext, msg);
            FullHttpRequest request = (FullHttpRequest) msg;
            connectionContext.wsCtx().path(request.uri());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse && ((HttpResponse) msg).status() == SWITCHING_PROTOCOLS) {
            LOGGER.debug("{} : read switch from backend {}", connectionContext, msg);
            ctx.write(msg).addListener(future -> {
                if (future.isSuccess()) {
                    configProtocolUpgrade(ctx);
                    promise.setSuccess();
                } else {
                    promise.setFailure(future.cause());
                }
            });
        } else {
            ctx.write(msg, promise);
        }
    }

    private void configProtocolUpgrade(ChannelHandlerContext ctx) {
        LOGGER.debug("{} : ws upgraded", connectionContext);

        List<ChannelHandler> addedHandlers = new ArrayList<>();
        addedHandlers.add(new HeadExceptionHandler(connectionContext));
        addedHandlers.add(new LoggingHandler("Frontend", LogLevel.TRACE));
        addedHandlers.add(new WebSocketFrameLogger("WS CLIENT RAW", connectionContext));
        addedHandlers.add(new WebSocket13FrameEncoder(false));
        addedHandlers.add(new WebSocket13FrameDecoder(WebSocketDecoderConfig.newBuilder()
                                                                                  .allowExtensions(true)
                                                                                  .allowMaskMismatch(true).build()));
        addedHandlers.forEach(h -> ctx.pipeline().addBefore(ctx.name(), null, h));

        //event handlers needs to come after to get incompress websocket frame
        ChannelHandlerContext compressionCtx = ctx.pipeline().context(WebSocketServerCompressionHandler.class);
        ctx.pipeline().addAfter(compressionCtx.name(), null, new WebSocketEventHandler(connectionContext));

        //remove the http1 handler completely
        ChannelHandlerContext httpCtx = ctx.pipeline().context(Http1FrontendHandler.class);
        if (httpCtx != null) {
            ctx.pipeline().remove(httpCtx.name());
        }
        ctx.pipeline().remove(ctx.name());
    }
}