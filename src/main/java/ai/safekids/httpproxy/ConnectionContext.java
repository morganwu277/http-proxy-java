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

package ai.safekids.httpproxy;

import ai.safekids.httpproxy.listener.NitmProxyListener;
import ai.safekids.httpproxy.tls.TlsContext;
import ai.safekids.httpproxy.handler.proxy.HttpProxyHandler;
import ai.safekids.httpproxy.handler.proxy.SocksProxyHandler;
import ai.safekids.httpproxy.handler.proxy.TransparentProxyHandler;
import ai.safekids.httpproxy.ws.WebSocketContext;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;

import static java.lang.String.*;

public class ConnectionContext {

    private NitmProxyMaster master;
    private HandlerProvider provider;
    private NitmProxyListener listener;

    private ByteBufAllocator alloc;

    private Address clientAddr;
    private Address serverAddr;

    private Channel clientChannel;
    private Channel serverChannel;

    private TlsContext tlsCtx;
    private WebSocketContext wsCtx;

    public ConnectionContext(NitmProxyMaster master) {
        this.master = master;
        this.provider = master.provider(this);
        this.tlsCtx = new TlsContext();
        this.wsCtx = new WebSocketContext();
        this.listener = master.listenerProvider().create();
    }

    public ConnectionContext withClientAddr(Address clientAddr) {
        this.clientAddr = clientAddr;
        return this;
    }

    public Address getClientAddr() {
        return clientAddr;
    }

    public ConnectionContext withServerAddr(Address serverAddr) {
        this.serverAddr = serverAddr;
        return this;
    }

    public Address getServerAddr() {
        return serverAddr;
    }

    public ConnectionContext withClientChannel(Channel clientChannel) {
        this.clientChannel = clientChannel;
        return this;
    }

    public ConnectionContext withServerChannel(Channel serverChannel) {
        this.serverChannel = serverChannel;
        return this;
    }

    public ConnectionContext withAlloc(ByteBufAllocator alloc) {
        this.alloc = alloc;
        return this;
    }

    public ByteBufAllocator alloc() {
        return alloc;
    }

    public NitmProxyMaster master() {
        return master;
    }

    public NitmProxyConfig config() {
        return master.config();
    }

    public ChannelHandler proxyHandler() {
        switch (master.config().getProxyMode()) {
            case HTTP:
                return new HttpProxyHandler(this);
            case SOCKS:
                return new SocksProxyHandler(master, this);
            case TRANSPARENT:
                return new TransparentProxyHandler(this);
            default:
                throw new IllegalStateException("No proxy mode available: " + master.config().getProxyMode());
        }
    }

    public HandlerProvider provider() {
        return provider;
    }

    public boolean connected() {
        return serverChannel != null;
    }

    public ChannelFuture connect(Address address, ChannelHandlerContext fromCtx) {
        if (serverChannel != null && (!serverAddr.equals(address) || !serverChannel.isActive())) {
            serverChannel.close();
            serverChannel = null;
        }
        if (serverChannel != null) {
            return serverChannel.newSucceededFuture();
        }

        tlsCtx.protocols(fromCtx.executor().newPromise());
        tlsCtx.protocol(fromCtx.executor().newPromise());
        serverAddr = address;
        return master.connect(fromCtx, this, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                withServerChannel(ch);
                ch.pipeline().addLast(provider().tlsBackendHandler());
                ch.pipeline().addLast(provider().tailBackendHandler());
                listener().onConnect(ConnectionContext.this, ch);
            }
        });
    }

    public Channel serverChannel() {
        return serverChannel;
    }

    public Channel clientChannel() {
        return clientChannel;
    }

    public TlsContext tlsCtx() {
        return tlsCtx;
    }

    public WebSocketContext wsCtx() {
        return wsCtx;
    }

    public NitmProxyListener listener() {
        return listener;
    }

    public void close() {
        listener.close(this);
    }

    @Override
    public String toString() {
        if (serverAddr != null) {
            return format("[Client (%s)] <=> [Server (%s)]",
                    clientAddr, serverAddr);
        }
        return format("[Client (%s)] <=> [PROXY]", clientAddr);
    }

    public String toString(boolean client) {
        if (client) {
            return format("[Client (%s)] <=> [PROXY]", clientAddr);
        } else {
            return format("[PROXY] <=> [Server (%s)]", serverAddr);
        }
    }
}
