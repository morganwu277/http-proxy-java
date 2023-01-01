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
import com.google.common.collect.Lists;
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
import io.netty.util.concurrent.PromiseCombiner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NitmProxyListenerManager implements NitmProxyListener {

    private final List<NitmProxyListener> listeners;
    private final List<NitmProxyListener> reversedListeners;

    public NitmProxyListenerManager(List<NitmProxyListener> listeners) {
        this.listeners =
            ImmutableList.<NitmProxyListener>builder().add(new HttpEventLogger()).addAll(listeners).build();
        this.reversedListeners = Lists.reverse(this.listeners);
    }

    @Override
    public void onInit(ConnectionContext connectionContext, Channel clientChannel) {
        listeners.forEach(listener -> listener.onInit(connectionContext, clientChannel));
    }

    @Override
    public void onConnect(ConnectionContext connectionContext, Channel serverChannel) {
        listeners.forEach(listener -> listener.onConnect(connectionContext, serverChannel));
    }

    @Override
    public void onHttpEvent(HttpEvent event) {
        listeners.forEach(listener -> listener.onHttpEvent(event));
    }

    @Override
    public Promise<Optional<FullHttpResponse>> onHttp1Request(ChannelHandlerContext ctx,
                                                              ConnectionContext connectionContext,
                                                              FullHttpRequest request) {
        //void promise
        Promise<Optional<FullHttpResponse>> finalResultPromise = ctx.executor().newPromise();
        Promise<Void> finishPromise = ctx.executor().newPromise();

        //combine promise completion
        PromiseCombiner promiseCombiner = new PromiseCombiner((ctx.executor()));

        //
        //Get all the futures of the requests
        //
        List<Future<Optional<FullHttpResponse>>> futures =
            listeners.stream().flatMap(l -> Stream.of(l.onHttp1Request(ctx, connectionContext, request)))
                     .collect(Collectors.toList());

        //
        // on completions of all promises, return the result
        //
        finishPromise.addListener(future -> {
            Optional<FullHttpResponse> result =
                futures.stream().map(Future::getNow).filter(Objects::nonNull).filter(Optional::isPresent)
                       .collect(Collectors.toList()).stream().findFirst().orElse(Optional.empty());
            finalResultPromise.setSuccess(result);
        });

        promiseCombiner.addAll(futures.toArray(new Future[0]));
        promiseCombiner.finish(finishPromise);

        return finalResultPromise;
    }

    @Override
    public List<HttpObject> onHttp1Response(ConnectionContext connectionContext, HttpObject response) {
        return reversedListeners
            .stream()
            .reduce(
                ImmutableList.of(response), (objects, listener) ->
                    objects.stream()
                           .flatMap(f -> listener.onHttp1Response(connectionContext, f).stream())
                           .collect(ImmutableList.toImmutableList()), (accu, objects) -> objects);
    }

    @Override
    public Future<Optional<Http2FramesWrapper>> onHttp2Request(ChannelHandlerContext ctx,
                                                               ConnectionContext connectionContext,
                                                               Http2FramesWrapper request) {
        //void promise
        Promise<Optional<Http2FramesWrapper>> finalResultPromise = ctx.executor().newPromise();
        Promise<Void> finishPromise = ctx.executor().newPromise();

        //combine promise completion
        PromiseCombiner promiseCombiner = new PromiseCombiner((ctx.executor()));

        //
        //Get all the futures of the requests
        //
        List<Future<Optional<Http2FramesWrapper>>> futures =
            listeners.stream().flatMap(l -> Stream.of(l.onHttp2Request(ctx, connectionContext, request)))
                     .collect(Collectors.toList());

        //
        // on completions of all promises, return the result
        //
        finishPromise.addListener(future -> {
            Optional<Http2FramesWrapper> result =
                futures.stream().map(Future::getNow).filter(Objects::nonNull).filter(Optional::isPresent)
                       .collect(Collectors.toList()).stream().findFirst().orElse(Optional.empty());
            finalResultPromise.setSuccess(result);
        });

        promiseCombiner.addAll(futures.toArray(new Future[0]));
        promiseCombiner.finish(finishPromise);

        return finalResultPromise;
    }

    public Boolean interceptHttp2Response(Http2Headers headers) {
        Boolean res =
            listeners.stream().map(l -> l.interceptHttp2Response(headers)).filter(Objects::nonNull).filter(t -> t)
                     .findFirst().orElse(false);
        return res;
    }

    @Override
    public Promise<Optional<FullHttp2Response>> onHttp2Response(ChannelHandlerContext ctx,
                                                                ConnectionContext connectionContext,
                                                                FullHttp2Response original) {
        //void promise
        Promise<Optional<FullHttp2Response>> finalResultPromise = ctx.executor().newPromise();
        Promise<Void> finishPromise = ctx.executor().newPromise();

        //combine promise completion
        PromiseCombiner promiseCombiner = new PromiseCombiner((ctx.executor()));

        //
        //Get all the futures of the requests
        //
        List<Future<Optional<FullHttp2Response>>> futures =
            listeners.stream().flatMap(l -> Stream.of(l.onHttp2Response(ctx, connectionContext, original)))
                     .collect(Collectors.toList());

        //
        // on completions of all promises, return the result
        //
        finishPromise.addListener(future -> {
            Optional<FullHttp2Response> result =
                futures.stream().map(Future::getNow).filter(Objects::nonNull).filter(Optional::isPresent)
                       .collect(Collectors.toList()).stream().findFirst().orElse(Optional.empty());
            finalResultPromise.setSuccess(result);
        });

        promiseCombiner.addAll(futures.toArray(new Future[0]));
        promiseCombiner.finish(finishPromise);

        return finalResultPromise;
    }

    @Override
    public List<Http2FrameWrapper<?>> onHttp2Response(ConnectionContext connectionContext, Http2FrameWrapper<?> frame) {
        return reversedListeners
            .stream()
            .reduce(
                ImmutableList.of(frame), (frames, listener) ->
                    frames.stream()
                          .flatMap(f ->
                                       listener.onHttp2Response(connectionContext, f).stream())
                          .collect(ImmutableList.toImmutableList()),
                (accu, frames) -> frames);
    }

    @Override
    public void onWsRequest(ConnectionContext connectionContext, WebSocketFrame frame) {
        listeners.forEach(listener -> listener.onWsRequest(connectionContext, frame));
    }

    @Override
    public void onWsResponse(ConnectionContext connectionContext, WebSocketFrame frame) {
        listeners.forEach(listener -> listener.onWsResponse(connectionContext, frame));
    }

    @Override
    public void onForwardEvent(ConnectionContext connectionContext, ForwardEvent event) {
        listeners.forEach(listener -> listener.onForwardEvent(connectionContext, event));
    }

    @Override
    public void onForwardRequest(ConnectionContext connectionContext, ByteBuf byteBuf) {
        listeners.forEach(listener -> listener.onForwardRequest(connectionContext, byteBuf));
    }

    @Override
    public void onForwardResponse(ConnectionContext connectionContext, ByteBuf byteBuf) {
        reversedListeners.forEach(listener -> listener.onForwardResponse(connectionContext, byteBuf));
    }

    @Override
    public void close(ConnectionContext connectionContext) {
        reversedListeners.forEach(listener -> listener.close(connectionContext));
    }
}
