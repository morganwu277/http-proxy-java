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
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.handler.protocol.ws.WebSocketFrontendHandler;
import ai.safekids.httpproxy.http.HttpHeadersUtil;
import ai.safekids.httpproxy.http.HttpUrl;
import ai.safekids.httpproxy.http.HttpUtil;
import ai.safekids.httpproxy.util.LogWrappers;
import ai.safekids.httpproxy.Protocols;
import ai.safekids.httpproxy.enums.ProxyMode;
import ai.safekids.httpproxy.event.OutboundChannelClosedEvent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Http1FrontendHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Http1FrontendHandler.class);

    private NitmProxyMaster master;
    private ConnectionContext connectionContext;
    private boolean tunneled;

    private List<ChannelHandler> addedHandlers = new ArrayList<>(3);

    public Http1FrontendHandler(NitmProxyMaster master, ConnectionContext connectionContext) {
        this.master = master;
        this.connectionContext = connectionContext;
        this.tunneled = connectionContext.connected();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);

        addedHandlers.add(new HttpServerCodec());
        addedHandlers.add(new HttpObjectAggregator(master.config().getMaxContentLength()));
        addedHandlers.add(connectionContext.provider().http1EventHandler());
        addedHandlers.forEach(handler -> ctx.pipeline().addBefore(ctx.name(), null, handler));
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        LOGGER.debug("{} : handlerRemoved", connectionContext);
        addedHandlers.forEach(handler -> ctx.pipeline().remove(handler));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (connectionContext.connected()) {
            connectionContext.serverChannel().close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }
        FullHttpRequest request = (FullHttpRequest) msg;
        if (master.config().getProxyMode() == ProxyMode.HTTP && !tunneled) {
            if (request.method() == HttpMethod.CONNECT) {
                handleTunnelProxyConnection(ctx, request);
            } else {
                handleHttpProxyConnection(ctx, request);
            }
        } else if (master.config().getProxyMode() == ProxyMode.TRANSPARENT && !connectionContext.connected()) {
            //handle web socket upgrade and add the handler as necessary
            if (HttpHeadersUtil.isWebSocketUpgrade(((FullHttpRequest) msg).headers())) {
                ctx.pipeline().addAfter(ctx.name(), null, new WebSocketFrontendHandler(connectionContext));
            }
            handleTransparentProxyConnection(ctx, request);
        } else {
            //handle web socket upgrade and add the handler as necessary
            if (HttpHeadersUtil.isWebSocketUpgrade(((FullHttpRequest) msg).headers()) && tunneled) {
                ctx.pipeline().addAfter(ctx.name(), null, new WebSocketFrontendHandler(connectionContext));
            }

            ctx.fireChannelRead(request);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        if (evt instanceof OutboundChannelClosedEvent) {
            if (tunneled) {
                ctx.close();
            }
        }
    }

    private void handleTunnelProxyConnection(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            Address address = Address.resolve(request.uri(), HttpUtil.HTTPS_PORT);
            connectionContext.connect(address, ctx).addListener((future) -> {
                if (!future.isSuccess()) {
                    ctx.close();
                }
            });
            FullHttpResponse response =
                new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            LOGGER.debug("{} : {}", connectionContext, LogWrappers.description(response));
            ctx.writeAndFlush(response);
            ctx.pipeline().replace(Http1FrontendHandler.this, null,
                                   connectionContext.provider().tlsFrontendHandler());
        } finally {
            request.release();
        }
    }

    private void handleHttpProxyConnection(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpUrl httpUrl = HttpUrl.resolve(request.uri());
        Address address = new Address(httpUrl.getHost(), httpUrl.getPort());
        request.setUri(httpUrl.getPath());
        connectionContext.connect(address, ctx).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                LOGGER.debug("{} : {}", connectionContext, LogWrappers.description(request));
                ctx.fireChannelRead(request);
            } else {
                request.release();
                ctx.channel().close();
            }
        });
        if (!connectionContext.tlsCtx().isNegotiated()) {
            connectionContext.tlsCtx().disableTls();
            connectionContext.tlsCtx().protocolPromise().setSuccess(Protocols.HTTP_1);
        }
    }

    private void handleTransparentProxyConnection(ChannelHandlerContext ctx, FullHttpRequest request) {
        Address address = Address.resolve(request.headers().get(HttpHeaderNames.HOST), HttpUtil.HTTP_PORT);
        connectionContext.connect(address, ctx).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                LOGGER.debug("{} : {}", connectionContext, LogWrappers.description(request));
                //future.channel().writeAndFlush(request);
                ctx.fireChannelRead(request);
            } else {
                request.release();
                ctx.channel().close();
            }
        });
        connectionContext.tlsCtx().disableTls();
        connectionContext.tlsCtx().protocolPromise().setSuccess(Protocols.HTTP_1);
    }
}