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

import ai.safekids.httpproxy.exception.NitmProxyException;
import ai.safekids.httpproxy.handler.ForwardBackendHandler;
import ai.safekids.httpproxy.handler.ForwardEventHandler;
import ai.safekids.httpproxy.handler.ForwardFrontendHandler;
import ai.safekids.httpproxy.handler.TailFrontendHandler;
import ai.safekids.httpproxy.handler.TailBackendHandler;
import ai.safekids.httpproxy.handler.protocol.ProtocolSelectHandler;
import ai.safekids.httpproxy.handler.protocol.http1.Http1BackendHandler;
import ai.safekids.httpproxy.handler.protocol.http1.Http1EventHandler;
import ai.safekids.httpproxy.handler.protocol.http1.Http1FrontendHandler;
import ai.safekids.httpproxy.handler.protocol.http2.Http2BackendHandler;
import ai.safekids.httpproxy.handler.protocol.http2.Http2EventHandler;
import ai.safekids.httpproxy.handler.protocol.http2.Http2FrontendHandler;
import ai.safekids.httpproxy.handler.protocol.tls.TlsBackendHandler;
import ai.safekids.httpproxy.handler.protocol.tls.TlsFrontendHandler;
import ai.safekids.httpproxy.handler.protocol.ws.WebSocketBackendHandler;
import ai.safekids.httpproxy.handler.protocol.ws.WebSocketEventHandler;
import ai.safekids.httpproxy.handler.protocol.ws.WebSocketFrontendHandler;
import io.netty.channel.ChannelHandler;

public class HandlerProvider {

    private NitmProxyMaster master;
    private ConnectionContext context;

    public HandlerProvider(NitmProxyMaster master, ConnectionContext context) {
        this.master = master;
        this.context = context;
    }

    public ChannelHandler protocolSelectHandler() {
        return new ProtocolSelectHandler(context);
    }

    public ChannelHandler frontendHandler(String protocol) {
        if (protocol.equals(Protocols.HTTP_1)) {
            return http1FrontendHandler();
        } else if (protocol.equals(Protocols.HTTP_2)) {
            return http2FrontendHandler();
        } else if (protocol.equals(Protocols.FORWARD)) {
            return forwardFrontendHandler();
        } else {
            throw new NitmProxyException("Unsupported protocol");
        }
    }

    public ChannelHandler backendHandler(String protocol) {
        if (protocol.equals(Protocols.HTTP_1)) {
            return http1BackendHandler();
        } else if (protocol.equals(Protocols.HTTP_2)) {
            return http2BackendHandler();
        } else if (protocol.equals(Protocols.FORWARD)) {
            return forwardBackendHandler();
        } else {
            throw new NitmProxyException("Unsupported protocol");
        }
    }

    public ChannelHandler http1BackendHandler() {
        return new Http1BackendHandler(context);
    }

    public ChannelHandler http1FrontendHandler() {
        return new Http1FrontendHandler(master, context);
    }

    public ChannelHandler wsBackendHandler() {
        return new WebSocketBackendHandler(context);
    }

    public ChannelHandler wsFrontendHandler() {
        return new WebSocketFrontendHandler(context);
    }

    public ChannelHandler wsEventHandler() {
        return new WebSocketEventHandler(context);
    }

    public ChannelHandler http1EventHandler() {
        return new Http1EventHandler(context);
    }

    public ChannelHandler http2BackendHandler() {
        return new Http2BackendHandler(context);
    }

    public ChannelHandler http2FrontendHandler() {
        return new Http2FrontendHandler(context);
    }

    public ChannelHandler http2EventHandler() {
        return new Http2EventHandler(context);
    }

    public ChannelHandler tlsFrontendHandler() {
        return new TlsFrontendHandler(context);
    }

    public ChannelHandler tlsBackendHandler() {
        return new TlsBackendHandler(master, context);
    }

    public ChannelHandler tailBackendHandler() {
        return new TailBackendHandler(context);
    }

    public ChannelHandler tailFrontendHandler() {
        return new TailFrontendHandler(context);
    }

    public ChannelHandler forwardFrontendHandler() {
        return new ForwardFrontendHandler(context);
    }

    public ChannelHandler forwardBackendHandler() {
        return new ForwardBackendHandler(context);
    }

    public ChannelHandler forwardEventHandler() {
        return new ForwardEventHandler(context);
    }
}
