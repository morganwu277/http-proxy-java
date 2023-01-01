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
import ai.safekids.httpproxy.listener.NitmProxyListener;
import ai.safekids.httpproxy.util.LogWrappers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketEventHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketEventHandler.class);

    private NitmProxyListener listener;
    private ConnectionContext connectionContext;

    /**
     * Create new instance of web socket event handler.
     *
     * @param connectionContext the connection context
     */
    public WebSocketEventHandler(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.listener = connectionContext.listener();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerRemoved", connectionContext);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
        throws Exception {
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("{} : WS SERVER WRITE web socket frame {} data:\n {}", connectionContext,
                             LogWrappers.description(msg), getFrameData(frame));
            } else {
                LOGGER.debug("{} : WS SERVER WRITE web socket frame {}", connectionContext,
                             LogWrappers.description(msg));
            }
            listener.onWsResponse(connectionContext, (WebSocketFrame) msg);
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("{} : WS CLIENT READ web socket frame {} data:\n {}", connectionContext,
                             LogWrappers.description(msg), getFrameData(frame));
            } else {
                LOGGER.debug("{} : WS CLIENT READ web socket frame {}", connectionContext,
                             LogWrappers.description(msg));
            }
            listener.onWsRequest(connectionContext, (WebSocketFrame) msg);
        }
        ctx.fireChannelRead(msg);
    }

    String getFrameData(WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            return ((TextWebSocketFrame) frame).text();
        }
        if (frame instanceof BinaryWebSocketFrame) {
            return ByteBufUtil.prettyHexDump(((BinaryWebSocketFrame) frame).content());
        }
        return "";
    }
}