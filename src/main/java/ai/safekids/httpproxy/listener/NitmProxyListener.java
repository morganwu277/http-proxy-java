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

package ai.safekids.httpproxy.listener;

import ai.safekids.httpproxy.handler.protocol.http2.FullHttp2Response;
import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.event.ForwardEvent;
import ai.safekids.httpproxy.event.HttpEvent;
import ai.safekids.httpproxy.handler.protocol.http2.Http2FrameWrapper;
import ai.safekids.httpproxy.handler.protocol.http2.Http2FramesWrapper;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.List;
import java.util.Optional;

public interface NitmProxyListener {

    /**
     * This callback will be invoked when the client channel was first initialized.
     *
     * @param connectionContext the connection context
     * @param clientChannel     the client channel
     */
    default void onInit(ConnectionContext connectionContext, Channel clientChannel) {
    }

    /**
     * This callback will be invoked when the server channel was connected.
     *
     * @param connectionContext the connection context
     * @param serverChannel     the server channel
     */
    default void onConnect(ConnectionContext connectionContext, Channel serverChannel) {
    }

    /**
     * This callback will be invoked when a full request and response was served.
     *
     * @param event the http event
     */
    default void onHttpEvent(HttpEvent event) {
    }

    /**
     * This callback will be invoked when receiving a request from client.
     *
     * @param connectionContext the connection context
     * @param request           the request
     * @return response if you want to intercept the request
     */
    default Future<Optional<FullHttpResponse>> onHttp1Request(ChannelHandlerContext ctx,
                                                              ConnectionContext connectionContext,
                                                              FullHttpRequest request) {
        Promise<Optional<FullHttpResponse>> promise = ctx.executor().newPromise();
        promise.setSuccess(Optional.empty());
        return promise;
    }

    /**
     * This callback will be invoked when receiving a response from server.
     *
     * @param connectionContext the connection context
     * @param response          the response
     * @return intercepted response objects, or you should send a list containing only the origin response
     */
    default List<HttpObject> onHttp1Response(ConnectionContext connectionContext, HttpObject response) {
        return ImmutableList.of(response);
    }

    /**
     * This callback will be invoked when receiving a request from client.
     *
     * @param connectionContext the connection context
     * @param request           the request
     * @return response if you want to intercept the request
     */
    default Future<Optional<Http2FramesWrapper>> onHttp2Request(ChannelHandlerContext ctx,
                                                                ConnectionContext connectionContext,
                                                                Http2FramesWrapper request) {
        Promise<Optional<Http2FramesWrapper>> promise = ctx.executor().newPromise();
        promise.setSuccess(Optional.empty());
        return promise;
    }

    /**
     * This callback will be invoked when receiving a response from server.
     *
     * @param connectionContext the connection context
     * @param frame             the response frame
     * @return intercepted response objects, or you should send a list containing only the origin response
     */
    default List<Http2FrameWrapper<?>> onHttp2Response(ConnectionContext connectionContext,
                                                       Http2FrameWrapper<?> frame) {
        return ImmutableList.of(frame);
    }

    /**
     * This can be implemented by the client to see if the response should be intercepted. Should be based usually on
     * the content type and perhaps other host or URI
     *
     * @param headers
     * @return
     */
    default Boolean interceptHttp2Response(Http2Headers headers) {
        return false;
    }

    /**
     * This is called when the reponse is intercepted with a new response
     */
    default Future<Optional<FullHttp2Response>> onHttp2Response(ChannelHandlerContext ctx,
                                                                ConnectionContext connectionContext,
                                                                FullHttp2Response original) {
        Promise<Optional<FullHttp2Response>> promise = ctx.executor().newPromise();
        promise.setSuccess(Optional.empty());
        return promise;
    }

    /**
     * This callback will be invoked while receiving a ws request from client.
     *
     * @param connectionContext the connection context
     * @param frame             the ws frame
     */
    default void onWsRequest(ConnectionContext connectionContext, WebSocketFrame frame) {
    }

    /**
     * This callback will be invoked while receiving a ws response from server.
     *
     * @param connectionContext the connection context
     * @param frame             the ws frame
     */
    default void onWsResponse(ConnectionContext connectionContext, WebSocketFrame frame) {
    }

    /**
     * This callback will be invoked while receiving a ws response from server.
     *
     * @param connectionContext the connection context
     * @param forwardEvent      the forward event
     */
    default void onForwardEvent(ConnectionContext connectionContext, ForwardEvent forwardEvent) {
    }

    /**
     * This callback will be invoked while receiving data from client.
     *
     * @param connectionContext the connection context
     * @param data              the data
     */
    default void onForwardRequest(ConnectionContext connectionContext, ByteBuf data) {
    }

    /**
     * This callback will be invoked while receiving data from server.
     *
     * @param connectionContext the connection context
     * @param data              the data
     */
    default void onForwardResponse(ConnectionContext connectionContext, ByteBuf data) {
    }

    /**
     * This callback will be called after the channel was closed, you should release objects if needed.
     *
     * @param connectionContext the connection context
     */
    default void close(ConnectionContext connectionContext) {
    }

    class Empty implements NitmProxyListener {
    }
}
