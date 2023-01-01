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
import ai.safekids.httpproxy.listener.NitmProxyListener;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.DefaultHttp2WindowUpdateFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ai.safekids.httpproxy.handler.protocol.http2.Http2FrameWrapper.*;
import static ai.safekids.httpproxy.util.LogWrappers.*;
import static io.netty.util.ReferenceCountUtil.*;

public class Http2RawInterceptHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http2RawInterceptHandler.class);
    private ConnectionContext connectionContext;
    private NitmProxyListener listener;

    public Http2RawInterceptHandler(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.listener = connectionContext.listener();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof Http2FrameWrapperHolder)) {
            ctx.write(msg, promise);
            return;
        }

        try {
            //get frame information
            List<Http2FrameWrapper<?>> frameWrappers =
                (List<Http2FrameWrapper<?>>) ((Http2FrameWrapperHolder) msg).getFrames();

            int streamId = frameWrappers.get(0).streamId();
            List<Http2Frame> http2Frames = frameWrappers.stream().map(f -> f.frame()).collect(Collectors.toList());
            Http2FrameHolder frameHolder = new Http2FrameHolder(streamId, http2Frames);

            //combine data streams
            ByteBuf content = frameHolder.getCompositeDateBuffWithAlloc(ctx).retainedDuplicate();

            final Http2Headers http2Headers = frameHolder.getHeaders();
            final boolean isCompressed = frameHolder.isCompressed();
            if (isCompressed) {
                int compressedByteSize = content.readableBytes();
                FullHttpResponse response =
                    HttpConversionUtil.toFullHttpResponse(streamId, http2Headers, content, false);

                LOGGER.debug("{} : decoding message for content type:'{}'", connectionContext,
                             http2Headers.get(HttpHeaderNames.CONTENT_ENCODING));
                FullHttpResponse decompressed = Http2Util.decompressResponse(response);
                content = decompressed.content();
                int decompressedByteSize = content.readableBytes();

                if (compressedByteSize == decompressedByteSize) {
                    LOGGER.debug("{} : no decompression happened for message {}", connectionContext,
                                 response.toString());
                } else {
                    LOGGER.debug("{} :  Encoding: {} Original Compressed:{} Decompressed:{} ", connectionContext,
                                 frameHolder.getContentEncoding(),
                                 compressedByteSize,
                                 decompressedByteSize);
                }
            }

            Future<Optional<FullHttp2Response>> intercept = getInterceptedResponse(ctx, http2Headers, content);

            intercept.addListener(f -> {
                if (f.isSuccess()) {
                    try {
                        LOGGER.debug("{} : ABBAS0a", connectionContext);
                        Optional<FullHttp2Response> optionalFullHttp2Response = intercept.getNow();

                        if (optionalFullHttp2Response.isPresent()) {

                            LOGGER.debug("{} : ABBAS0b", connectionContext);
                            FullHttp2Response http2Response = optionalFullHttp2Response.get();
                            Http2Headers customHeaders = http2Response.getHeaders();
                            ByteBuf customContent = http2Response.getContent();

                            if (isCompressed) {
                                int decompressedByteSize = customContent.readableBytes();
                                String encoding = frameHolder.getContentEncoding();
                                FullHttpResponse response =
                                    HttpConversionUtil.toFullHttpResponse(streamId, http2Headers, customContent, false);

                                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, customContent.readableBytes());
                                response.headers().remove(HttpHeaderNames.CONTENT_ENCODING);

                                HttpContent compressed = Http2Util.compressResponse(response, encoding);
                                customContent = compressed.content();
                                int compressedByteSize = customContent.readableBytes();
                                LOGGER.debug("{} :  Custom Compressed:{} Decompressed:{} ", connectionContext,
                                             compressedByteSize,
                                             decompressedByteSize);
                            }

                            //write frames
                            EmbeddedChannel channel = new EmbeddedChannel(
                                new EmbeddedFrameReaderHandler(connectionContext),
                                new EmbeddedFrameWriterHandler(connectionContext)
                            );

                            MessageToFrameHolder messageHolder =
                                new MessageToFrameHolder(streamId, http2Headers, customContent,
                                                         frameHolder.getFrameSettings().getMaxFrameSize());

                            LOGGER.debug("{} : ABBAS1", connectionContext);
                            channel.writeOutbound(messageHolder);

                            boolean finishedRead = false;
                            List<Http2FrameWrapper<?>> frameList = new ArrayList<>();
                            int bytesAboutToWrite = 0;
                            do {
                                LOGGER.debug("{} : ABBAS2", connectionContext);
                                Http2FrameWrapper<?> ret = (Http2FrameWrapper) channel.readOutbound();
                                if (ret != null) {
                                    bytesAboutToWrite +=
                                        ret.isData()? ((Http2DataFrame) ret.frame()).content().readableBytes() : 0;
                                    frameList.add(ret);
                                } else {
                                    finishedRead = true;
                                }
                            } while (!finishedRead);

                            LOGGER.debug("{} : ABBAS3 total bytes flushing:{}", connectionContext, bytesAboutToWrite);
                            writeFrames(ctx, frameList, promise); //write the original message
                            return;
                        } else {
                            LOGGER.debug("{} : ABBAS4", connectionContext);
                            writeFrames(ctx, frameWrappers, promise); //write the original message
                            return;
                        }
                    } catch (Exception e) {
                        LOGGER.error("{} : Unable to intercept HTTP2 response", connectionContext, e);
                        promise.setFailure(e);
                    }

                    if (!f.isSuccess()) {
                        LOGGER.error("{} : Unable to intercept HTTP response", connectionContext, f.cause());
                        promise.setFailure(f.cause());
                        return;
                    }
                }

                LOGGER.debug("{} : ABBAS5", connectionContext);
                writeFrames(ctx, frameWrappers, promise); //write the original message
                return;
            });
        } catch (Exception e) {
            LOGGER.error("unable to write", e);
            throw e;
        }
    }

    public void writeFrames(ChannelHandlerContext ctx, List<Http2FrameWrapper<?>> frames, ChannelPromise promise) {

        PromiseCombiner promiseCombiner = new PromiseCombiner(ctx.executor());

        for (Http2FrameWrapper<?> frame : frames) {
            ChannelPromise channelPromise = ctx.newPromise();
            ChannelFuture future = ctx.write(frame, channelPromise);
            promiseCombiner.add(future);
        }
        promiseCombiner.finish(promise);
    }

    public Future<Optional<FullHttp2Response>> getInterceptedResponse(ChannelHandlerContext ctx, Http2Headers headers,
                                                                      ByteBuf content) {
        Future<Optional<FullHttp2Response>> future =
            listener.onHttp2Response(ctx, connectionContext, new FullHttp2Response(headers, content));
        return future;
    }

    /**
     * Responsible for writing the custom content to HTTP2 Frames
     */
    public class EmbeddedFrameWriterHandler extends ChannelDuplexHandler {
        private ConnectionContext connectionContext;

        public EmbeddedFrameWriterHandler(ConnectionContext connectionContext) {
            this.connectionContext = connectionContext;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            LOGGER.debug("{} : handlerAdded", connectionContext);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof MessageToFrameHolder)) {
                ctx.write(msg, promise);
                return;
            }

            MessageToFrameHolder holder = (MessageToFrameHolder) msg;

            int streamId = holder.getStreamId();
            Http2Headers http2Headers = holder.getHttp2Headers();
            ByteBuf content = holder.getContent();
            Integer maxFrameSize = holder.getMaxFrameSize();

            DefaultHttp2HeadersEncoder headersEncoder = new DefaultHttp2HeadersEncoder();
            DefaultHttp2FrameWriter frameWriter = new DefaultHttp2FrameWriter(headersEncoder);

            if (maxFrameSize != null) {
                LOGGER.debug("{} : maxFrameSize:{}", connectionContext, maxFrameSize);
                frameWriter.maxFrameSize(maxFrameSize.intValue());
            }

            PromiseCombiner promiseCombiner = new PromiseCombiner(ctx.executor());

            //write header and body frame
            ChannelPromise headerFramePromise = ctx.newPromise();
            ChannelPromise dataFramePromise = ctx.newPromise();
            ChannelPromise dataEmptyFramePromise = ctx.newPromise();

            LOGGER.debug("{} : writing headerbytes", connectionContext);

            http2Headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(content.readableBytes()));

            ChannelFuture headerFuture =
                frameWriter.writeHeaders(ctx, streamId, http2Headers, 0, false, headerFramePromise);

            LOGGER.debug("{} : wrote {} databytes", connectionContext, content.readableBytes());

            ChannelFuture dataFuture = frameWriter.writeData(ctx, streamId, content, 0, false, dataFramePromise);

            ChannelFuture dataEmptyFuture =
                frameWriter.writeData(ctx, streamId, ctx.alloc().buffer(0), 0, true, dataEmptyFramePromise);

            promiseCombiner.add(headerFuture);
            promiseCombiner.add(dataFuture);
            promiseCombiner.add(dataEmptyFuture);
            promiseCombiner.finish(promise);
        }
    }

    public class EmbeddedFrameReaderHandler extends ChannelDuplexHandler implements Http2FrameListener {
        private ConnectionContext connectionContext;
        Http2FrameReader frameReader = new DefaultHttp2FrameReader();

        public EmbeddedFrameReaderHandler(ConnectionContext connectionContext) {
            this.connectionContext = connectionContext;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            LOGGER.debug("{} : handlerAdded", connectionContext);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof ByteBuf)) {
                ctx.write(msg, promise);
                return;
            }
            frameReader.readFrame(ctx, (ByteBuf) msg, this);
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
            LOGGER.debug("ABBAS: onDataRead:" + data.readableBytes());
            int processed = data.readableBytes() + padding;
            Http2DataFrameWrapper frame =
                frameWrapper(streamId, new DefaultHttp2DataFrame(data.copy(), endOfStream, padding));
            ctx.write(touch(frame, format("%s context=%s", frame, connectionContext)));
            return processed;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                                  boolean endOfStream) {
            LOGGER.debug("ABBAS: onHeaderRead");
            ctx.write(frameWrapper(streamId, new DefaultHttp2HeadersFrame(headers, endOfStream, padding)));
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                                  short weight, boolean exclusive, int padding, boolean endOfStream) {
            LOGGER.debug("ABBAS: onHeadersRead");
            ctx.write(frameWrapper(streamId, new DefaultHttp2HeadersFrame(headers, endOfStream, padding)));
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                                   boolean exclusive) {
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
            LOGGER.debug("ABBAS: onRstStreamRead");
            ctx.write(frameWrapper(streamId, new DefaultHttp2ResetFrame(errorCode)));
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) {
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
            LOGGER.debug("ABBAS: onSettingsRead");
            ctx.write(frameWrapper(0, new DefaultHttp2SettingsFrame(settings)));
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) {
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) {
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                      Http2Headers headers, int padding) {
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
            LOGGER.debug("ABBAS: onWindowUpdateRead");
            ctx.write(frameWrapper(streamId, new DefaultHttp2WindowUpdateFrame(windowSizeIncrement)));
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                                   ByteBuf payload) {
        }
    }

    public class MessageToFrameHolder {
        int streamId;
        Http2Headers http2Headers;
        ByteBuf content;
        Integer maxFrameSize;

        public MessageToFrameHolder(int streamId, Http2Headers http2Headers, ByteBuf content, Integer maxFrameSize) {
            this.streamId = streamId;
            this.http2Headers = http2Headers;
            this.content = content;
            this.maxFrameSize = maxFrameSize;
        }

        public int getStreamId() {
            return streamId;
        }

        public void setStreamId(int streamId) {
            this.streamId = streamId;
        }

        public Http2Headers getHttp2Headers() {
            return http2Headers;
        }

        public void setHttp2Headers(Http2Headers http2Headers) {
            this.http2Headers = http2Headers;
        }

        public ByteBuf getContent() {
            return content;
        }

        public void setContent(ByteBuf content) {
            this.content = content;
        }

        public Integer getMaxFrameSize() {
            return maxFrameSize;
        }

        public void setMaxFrameSize(Integer maxFrameSize) {
            this.maxFrameSize = maxFrameSize;
        }
    }
}