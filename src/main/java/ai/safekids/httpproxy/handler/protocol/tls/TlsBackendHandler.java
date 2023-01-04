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

package ai.safekids.httpproxy.handler.protocol.tls;

import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.NitmProxyMaster;
import ai.safekids.httpproxy.Protocols;
import ai.safekids.httpproxy.exception.NitmProxyException;
import ai.safekids.httpproxy.tls.TlsUtil;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslMasterKeyHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.netty.handler.ssl.ApplicationProtocolNames.*;
import static java.lang.String.*;

public class TlsBackendHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TlsBackendHandler.class);

    private NitmProxyMaster master;
    private ConnectionContext connectionContext;

    private final List<Object> pendings = new ArrayList<>();

    public TlsBackendHandler(NitmProxyMaster master, ConnectionContext connectionContext) {
        this.master = master;
        this.connectionContext = connectionContext;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);

        connectionContext.tlsCtx().protocolsPromise().addListener(future -> {
            if (!future.isSuccess()) {
                LOGGER.debug("{} : (1) Unable to get tls protocol promise", connectionContext, future.cause());
                ctx.close();
            } else if (connectionContext.tlsCtx().isEnabled()) {
                LOGGER.debug("{} : Configuring SSL", connectionContext);
                configSsl(ctx);
            } else if (connectionContext.tlsCtx().protocolPromise().isSuccess()) {
                LOGGER.debug("{} : Configuring protocol", connectionContext);
                configureProtocol(ctx, connectionContext.tlsCtx().protocol());
            } else if (connectionContext.tlsCtx().protocolPromise().isDone()) {
                LOGGER.debug("{} : Configuring protocol is done", connectionContext);
                ctx.close();
            } else {
                connectionContext.tlsCtx().protocolPromise().addListener(protocolFuture -> {
                    if (protocolFuture.isSuccess()) {
                        configureProtocol(ctx, connectionContext.tlsCtx().protocol());
                    } else {
                        LOGGER.debug("{} : (2) Unable to get tls protocol promise", connectionContext,
                                     protocolFuture.cause());
                        ctx.close();
                    }
                });
            }
        });
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerRemoved", connectionContext);

        flushPendings(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        synchronized (pendings) {
            pendings.add(msg);
        }
        if (ctx.isRemoved()) {
            flushPendings(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : channelInactive", connectionContext);
        connectionContext.clientChannel().close();
        synchronized (pendings) {
            pendings.forEach(ReferenceCountUtil::release);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error(format("%s : exceptionCaught with %s", connectionContext, cause.getMessage()), cause);
        ctx.close();
    }

    private SslHandler sslHandler(ByteBufAllocator alloc) throws SSLException {
        return TlsUtil.ctxForClient(connectionContext).newHandler(alloc, connectionContext.getServerAddr().getHost(),
                                                                  connectionContext.getServerAddr().getPort());
    }

    private void flushPendings(ChannelHandlerContext ctx) {
        synchronized (pendings) {
            Iterator<Object> iterator = pendings.iterator();
            while (iterator.hasNext()) {
                ctx.write(iterator.next());
                iterator.remove();
            }
            ctx.flush();
        }
    }

    private void configureProtocol(ChannelHandlerContext ctx, String protocol) {
        try {
            ctx.pipeline().replace(this, null, connectionContext.provider().backendHandler(protocol));
        } catch (NitmProxyException e) {
            LOGGER.error("{} : Unsupported protocol", connectionContext);
            ctx.close();
        }
    }

    /**
     * Configure for ssl.
     *
     * @param ctx the channel handler context
     * @throws SSLException if ssl failure
     */
    private void configSsl(ChannelHandlerContext ctx) throws SSLException {
        SslHandler sslHandler = sslHandler(ctx.alloc());
        ctx.pipeline().addBefore(ctx.name(), null, sslHandler)
           .addBefore(ctx.name(), null, new AlpnHandler(ctx, getFallbackProtocol()));
    }

    private String getFallbackProtocol() {
        if (connectionContext.tlsCtx().isNegotiated()) {
            return connectionContext.tlsCtx().protocol();
        }
        if (connectionContext.tlsCtx().protocolsPromise().isSuccess() &&
            connectionContext.tlsCtx().protocols() != null &&
            connectionContext.tlsCtx().protocols().contains(HTTP_1_1)) {
            return HTTP_1_1;
        }
        return Protocols.FORWARD;
    }

    private class AlpnHandler extends ApplicationProtocolNegotiationHandler {
        private ChannelHandlerContext tlsCtx;

        private AlpnHandler(ChannelHandlerContext tlsCtx, String fallbackProtocol) {
            super(fallbackProtocol);
            this.tlsCtx = tlsCtx;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            LOGGER.debug("{} : handlerAdded", connectionContext);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
            LOGGER.debug("{} : Configuring protocol {}", connectionContext, protocol);
            if (!connectionContext.tlsCtx().isNegotiated()) {
                connectionContext.tlsCtx().protocolPromise().setSuccess(protocol);
            }
            if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                LOGGER.debug("{} : configured HTTP1 protocol", connectionContext);
                configureProtocol(tlsCtx, Protocols.HTTP_1);
            } else if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                LOGGER.debug("{} : configured HTTP2 protocol", connectionContext);
                configureProtocol(tlsCtx, Protocols.HTTP_2);
            } else {
                LOGGER.debug("{} : configured Forward protocol", connectionContext);
                configureProtocol(tlsCtx, Protocols.FORWARD);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            if (!connectionContext.tlsCtx().isNegotiated()) {
                connectionContext.tlsCtx().protocolPromise().setFailure(cause);
            }
        }
    }
}
