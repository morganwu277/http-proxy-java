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

package ai.safekids.httpproxy.handler.proxy;

import ai.safekids.httpproxy.Address;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.NitmProxyMaster;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SocksProxyHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocksProxyHandler.class);

    private ConnectionContext connectionContext;

    private List<ChannelHandler> handlers;

    public SocksProxyHandler(NitmProxyMaster master,
                             ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;

        handlers = new ArrayList<>();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext.toString(true));

        Socks5ServerEncoder socks5ServerEncoder = new Socks5ServerEncoder(Socks5AddressEncoder.DEFAULT);
        SocksPortUnificationServerHandler socksPortUnificationServerHandler =
                new SocksPortUnificationServerHandler(socks5ServerEncoder);
        ctx.pipeline().addBefore(ctx.name(), null, socksPortUnificationServerHandler);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerRemoved", connectionContext.toString(true));

        handlers.forEach(ctx.pipeline()::remove);
        handlers.clear();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage socksMessage)
            throws Exception {
        switch (socksMessage.version()) {
            case SOCKS4a:
                Socks4CommandRequest socksV4CmdRequest = (Socks4CommandRequest) socksMessage;
                if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
                    onSocksSuccess(ctx, socksV4CmdRequest);
                } else {
                    ctx.close();
                }
                break;
            case SOCKS5:
                if (socksMessage instanceof Socks5InitialRequest) {
                    ctx.pipeline().addFirst(addChannelHandler(new Socks5CommandRequestDecoder()));
                    ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                } else if (socksMessage instanceof Socks5PasswordAuthRequest) {
                    ctx.pipeline().addFirst(addChannelHandler(new Socks5CommandRequestDecoder()));
                    ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                } else if (socksMessage instanceof Socks5CommandRequest) {
                    Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksMessage;
                    if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        onSocksSuccess(ctx, socks5CmdRequest);
                    } else {
                        ctx.close();
                    }
                } else {
                    ctx.close();
                }
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    private <T extends ChannelHandler> T addChannelHandler(T channelHandler) {
        handlers.add(channelHandler);
        return channelHandler;
    }

    private void onSocksSuccess(ChannelHandlerContext ctx, Socks4CommandRequest request) {
        Address serverAddr = new Address(request.dstAddr(), request.dstPort());
        connectionContext.connect(serverAddr, ctx).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                ctx.writeAndFlush(new DefaultSocks4CommandResponse(
                        Socks4CommandStatus.SUCCESS,
                        request.dstAddr(),
                        request.dstPort()));
                onServerConnected(ctx);
            } else {
                ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(
                        Socks4CommandStatus.REJECTED_OR_FAILED,
                        request.dstAddr(),
                        request.dstPort()));
                ctx.close();
            }
        });
    }

    private void onSocksSuccess(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        Address serverAddr = new Address(request.dstAddr(), request.dstPort());
        connectionContext.connect(serverAddr, ctx).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        request.dstAddrType(),
                        request.dstAddr(),
                        request.dstPort()));
                onServerConnected(ctx);
            } else {
                ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.FAILURE,
                        request.dstAddrType(),
                        request.dstAddr(),
                        request.dstPort()));
                ctx.close();
            }
        });
    }

    private void onServerConnected(ChannelHandlerContext ctx) {
        ctx.pipeline().replace(SocksProxyHandler.this, null,
                connectionContext.provider().tlsFrontendHandler());
    }
}
