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

package ai.safekids.httpproxy.handler.protocol.http2;

import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.event.HttpEvent;
import ai.safekids.httpproxy.http.HttpUtil;
import ai.safekids.httpproxy.listener.NitmProxyListener;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static ai.safekids.httpproxy.http.HttpHeadersUtil.*;
import static io.netty.util.ReferenceCountUtil.*;
import static java.lang.System.*;

public class Http2EventHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http2EventHandler.class);

    private NitmProxyListener listener;
    private ConnectionContext connectionContext;

    private Map<Integer, FrameCollector> streams = new ConcurrentHashMap<>();

    private Map<Integer, FrameWrapperCollector> streamsWrapper = new ConcurrentHashMap<>();

    private List<Integer> streamsToIntercept = Collections.synchronizedList(new ArrayList<>());

    /**
     * Create new instance of http1 event handler.
     *
     * @param connectionContext the connection context
     */
    public Http2EventHandler(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.listener = connectionContext.listener();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);
        ctx.pipeline().addBefore(ctx.name(), null, new Http2RawInterceptHandler(connectionContext));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof Http2FrameWrapper)) {
            ctx.write(msg, promise);
            return;
        }

        Http2FrameWrapper<?> frameWrapper = (Http2FrameWrapper<?>) msg;
        FrameCollector frameCollector = streams.computeIfAbsent(frameWrapper.streamId(), this::newFrameCollector);

        FrameWrapperCollector frameWrapperCollector =
            streamsWrapper.computeIfAbsent(frameWrapper.streamId(), this::newFrameWrapperCollector);

        boolean intercepting = streamsToIntercept.contains(frameWrapper.streamId());

        if (frameWrapper.isHeaders() && !intercepting) {
            Http2Headers headers = ((Http2HeadersFrame) frameWrapper.frame()).headers();
            intercepting = listener.interceptHttp2Response(headers);
            if (intercepting) {
                streamsToIntercept.add(frameWrapper.streamId());
            }
        }

        boolean streamEnded = frameWrapper.isEndStream();
        boolean isHeaderOrDataFrame = frameWrapper.isData() || frameWrapper.isHeaders();

        //flush out if not intercepting or a non header or data frame
        if (!intercepting || !isHeaderOrDataFrame) {
            frameCollector.onResponseFrame(frameWrapper.frame());
            ctx.write(frameWrapper, promise);
        } else {
            frameWrapperCollector.add(frameWrapper);
        }

        LOGGER.debug("{} received the following frame {}", connectionContext,
                     frameWrapper.frame().getClass().getName());

        if (streamEnded) {
            if (intercepting) {
                LOGGER.debug("{} flushing {} frames at end of stream", connectionContext,
                             frameWrapperCollector.getFrames().size());
                writeFrames(ctx, frameWrapperCollector.getFrames(), promise);
            }
            try {
                frameCollector.collect().ifPresent(listener::onHttpEvent);
            } finally {
                frameCollector.release();
                streams.remove(frameWrapper.streamId());
                streamsWrapper.remove(frameWrapper.streamId());
                streamsToIntercept.remove(Integer.valueOf(frameWrapper.streamId()));
            }
        }
    }

    private void writeFrames(ChannelHandlerContext ctx, List<Http2FrameWrapper<?>> frames, ChannelPromise promise) {
        try {
            if (frames.isEmpty()) {
                promise.setSuccess();
            } else {
                ctx.write(new Http2FrameWrapperHolder(frames), promise);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Http2FrameWrapper) || (!Http2FrameWrapper.isFrame(msg, Http2HeadersFrame.class) &&
                                                    !Http2FrameWrapper.isFrame(msg, Http2DataFrame.class))) {
            ctx.fireChannelRead(msg);
            return;
        }

        Http2FrameWrapper<?> frameWrapper = (Http2FrameWrapper<?>) msg;
        FrameCollector frameCollector = streams.computeIfAbsent(frameWrapper.streamId(), this::newFrameCollector);
        Optional<Http2FramesWrapper> requestOptional = frameCollector.onRequestFrame(frameWrapper.frame());
        if (!requestOptional.isPresent()) {
            return;
        }

        Http2FramesWrapper request = requestOptional.get();

        /**
         * Create a new promise to receive the callback
         */
        try {
            Future<Optional<Http2FramesWrapper>> responsePromise =
                listener.onHttp2Request(ctx, connectionContext, request);
            responsePromise.addListener(p -> {
                if (responsePromise.isSuccess()) {
                    Optional<Http2FramesWrapper> responseOptional = responsePromise.getNow();
                    if (!responseOptional.isPresent()) {
                        request.getAllFrames().forEach(ctx::fireChannelRead);
                        return;
                    }
                    try {
                        Http2FramesWrapper response = responseOptional.get();
                        frameCollector.onResponseHeadersFrame(response.getHeaders());
                        response.getData().forEach(frameCollector::onResponseDataFrame);
                        frameCollector.collect().ifPresent(listener::onHttpEvent);
                        response.getAllFrames().forEach(ctx::write);
                        ctx.flush();
                    } finally {
                        release(request);
                        frameCollector.release();
                        streams.remove(frameWrapper.streamId());
                    }
                } else {
                    request.getAllFrames().forEach(ctx::fireChannelRead);
                    return;
                }
            });

        } catch (Exception e) {
            LOGGER.error("onHttp2Request exception", e);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        streams.values().forEach(FrameCollector::release);
    }

    private FrameCollector newFrameCollector(int streamId) {
        return new FrameCollector(streamId, HttpEvent.builder(connectionContext));
    }

    private FrameWrapperCollector newFrameWrapperCollector(int streamId) {
        return new FrameWrapperCollector(streamId);
    }

    private static class FrameCollector {

        private int streamId;
        private HttpEvent.Builder httpEventBuilder;
        private Http2HeadersFrame requestHeader;
        private List<Http2DataFrame> requestData = new ArrayList<>();

        private boolean requestDone;

        public FrameCollector(int streamId, HttpEvent.Builder httpEventBuilder) {
            this.streamId = streamId;
            this.httpEventBuilder = httpEventBuilder;
        }

        /**
         * Handles a http2 frame of the request, and return full request frames while the request was ended.
         *
         * @param frame a http2 frame
         * @return full request frames if the request was ended, return empty if there are more frames of the request
         */
        public Optional<Http2FramesWrapper> onRequestFrame(Http2Frame frame) {
            if (frame instanceof Http2HeadersFrame) {
                requestHeader = (Http2HeadersFrame) frame;
                Http2Headers headers = requestHeader.headers();
                httpEventBuilder.method(HttpMethod.valueOf(headers.method().toString())).version(HttpUtil.HTTP_2)
                                .host(headers.authority().toString()).path(headers.path().toString())
                                .requestTime(currentTimeMillis());
                requestDone = requestHeader.isEndStream();
            } else if (frame instanceof Http2DataFrame) {
                Http2DataFrame data = (Http2DataFrame) frame;
                requestData.add(data);
                httpEventBuilder.addRequestBodySize(data.content().readableBytes());
                requestDone = data.isEndStream();
            }

            if (requestDone) {
                Http2FramesWrapper request =
                    Http2FramesWrapper.builder(streamId).headers(requestHeader).data(requestData).build();
                requestData.clear();
                return Optional.of(request);
            }
            return Optional.empty();
        }

        public boolean onResponseFrame(Http2Frame frame) {
            if (frame instanceof Http2HeadersFrame) {
                return onResponseHeadersFrame((Http2HeadersFrame) frame);
            }
            if (frame instanceof Http2DataFrame) {
                return onResponseDataFrame((Http2DataFrame) frame);
            }
            return false;
        }

        public boolean onResponseHeadersFrame(Http2HeadersFrame frame) {
            Http2Headers headers = frame.headers();
            httpEventBuilder.status(getStatus(headers)).contentType(getContentType(headers))
                            .responseTime(currentTimeMillis());
            return frame.isEndStream();
        }

        public boolean onResponseDataFrame(Http2DataFrame frame) {
            httpEventBuilder.addResponseBodySize(frame.content().readableBytes());
            return frame.isEndStream();
        }

        public Optional<HttpEvent> collect() {
            if (requestDone) {
                return Optional.of(httpEventBuilder.build());
            }
            return Optional.empty();
        }

        public void release() {
            requestData.forEach(ReferenceCountUtil::release);
        }

        public List<Http2DataFrame> getRequestData() {
            return requestData;
        }
    }

    private static class FrameWrapperCollector {

        private int streamId;
        private List<Http2FrameWrapper<?>> frames = new ArrayList<>();

        private boolean requestDone;

        public FrameWrapperCollector(int streamId) {
            this.streamId = streamId;
        }

        public void add(Http2FrameWrapper<?> frameWrapper) {
            frames.add(frameWrapper);
        }

        public List<Http2FrameWrapper<?>> getFrames() {
            return frames;
        }
    }
}
