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
import ai.safekids.httpproxy.exception.NitmProxyException;
import ai.safekids.httpproxy.handler.HeadExceptionHandler;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.DefaultHttp2WindowUpdateFrame;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2WindowUpdateFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static ai.safekids.httpproxy.handler.protocol.http2.Http2FrameWrapper.*;
import static ai.safekids.httpproxy.util.LogWrappers.*;
import static io.netty.handler.logging.LogLevel.*;
import static io.netty.util.ReferenceCountUtil.*;

public class Http2BackendHandler
    extends ChannelDuplexHandler
    implements Http2FrameListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Http2BackendHandler.class);

    private ConnectionContext connectionContext;
    private Http2ConnectionHandler http2ConnectionHandler;

    private ChannelPromise ready;
    private AtomicInteger currentStreamId = new AtomicInteger(1);
    private BiMap<Integer, Integer> streams = Maps.synchronizedBiMap(HashBiMap.create());

    private Http2Settings http2Settings;

    public Http2BackendHandler(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("{} : handlerAdded", connectionContext);

        Http2Connection http2Connection = new DefaultHttp2Connection(false);
        http2ConnectionHandler = new Http2ConnectionHandlerBuilder()
            .connection(http2Connection)
            .frameListener(this)
            .frameLogger(new Http2FrameLogger(TRACE, this.getClass()))
            .build();
        ctx.pipeline()
           .addBefore(ctx.name(), null, new HeadExceptionHandler(connectionContext))
           .addBefore(ctx.name(), null, http2ConnectionHandler);

        ready = ctx.newPromise();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
        throws Exception {
        if (!(msg instanceof Http2FrameWrapper)) {
            LOGGER.debug("{} : expecting http2 frame wrapper but received {}", connectionContext, msg.getClass());
            ctx.write(msg, promise);
            return;
        }
        Http2FrameWrapper<?> frame = (Http2FrameWrapper<?>) msg;

        if (ready.isSuccess()) {
            log(frame);
            frame.write(ctx, http2ConnectionHandler.encoder(), getUpstreamStreamId(frame.streamId()),
                        promise);
            ctx.flush();
        } else {
            ready.addListener(ignore -> {
                log(frame);
                frame.write(ctx, http2ConnectionHandler.encoder(), getUpstreamStreamId(frame.streamId()),
                            promise);
                ctx.flush();
            });
        }
    }

//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        LOGGER.debug("{} : exceptionCaught with {}", connectionContext, cause.getMessage());
//        ctx.close();
//    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        LOGGER.debug("{} : channelInactive", connectionContext);
        if (!ready.isDone()) {
            ready.setFailure(new NitmProxyException("Channel was closed"));
        }
        connectionContext.clientChannel().close();
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                          boolean endOfStream) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read data frame from server streamId:{} endOfStream:{}", connectionContext, streamId,
                         endOfStream);
        }
        int originStreamId = getOriginStreamId(streamId);
        int processed = data.readableBytes() + padding;
        Http2DataFrameWrapper frame = frameWrapper(originStreamId,
                                                   new DefaultHttp2DataFrame(data.copy(), endOfStream, padding));
        connectionContext.clientChannel().writeAndFlush(touch(frame,
                                                              format("%s context=%s", frame, connectionContext)));
        return processed;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                              int padding, boolean endOfStream) {
        int originStreamId = getOriginStreamId(streamId);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read headers frame from server streamId:{} endOfStream:{}\n{}", connectionContext,
                         originStreamId,
                         endOfStream, headers);
        }
        connectionContext.clientChannel().writeAndFlush(
            frameWrapper(originStreamId, new DefaultHttp2HeadersFrame(headers, endOfStream, padding)));
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                              int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) {
        int originStreamId = getOriginStreamId(streamId);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read headers frame from server streamId:{} endOfStream:{}\n{}", connectionContext,
                         originStreamId,
                         endOfStream, headers);
        }
        connectionContext.clientChannel().writeAndFlush(
            frameWrapper(originStreamId, new DefaultHttp2HeadersFrame(headers, endOfStream, padding)));
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                               short weight, boolean exclusive) {
        int originStreamId = getOriginStreamId(streamId);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read priority frame from server streamId:{}", connectionContext, originStreamId);
        }
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        int originStreamId = getOriginStreamId(streamId);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read RST stream from server streamId:{} errorCode:{}",
                         connectionContext, originStreamId,
                         errorCode);
        }
        connectionContext.clientChannel().writeAndFlush(
            frameWrapper(originStreamId, new DefaultHttp2ResetFrame(errorCode)));
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read settings ACK from server", connectionContext);
        }
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
        this.http2Settings = settings;
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read settings frame from server {}", connectionContext, settings);
        }
        ready.setSuccess();
        connectionContext.clientChannel().writeAndFlush(
            frameWrapper(0,
                         new DefaultHttp2SettingsFrame(settings)));
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
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                             ByteBuf debugData) {
        int originStreamId = getOriginStreamId(lastStreamId);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : go away read frame for streamId:{} errorCode:{} debugData:{}",
                         connectionContext, lastStreamId, errorCode, ByteBufUtil.prettyHexDump(debugData));
        }
        DefaultHttp2GoAwayFrame frame = new DefaultHttp2GoAwayFrame(errorCode, debugData);
        frame.setExtraStreamIds(lastStreamId);
        connectionContext.clientChannel().writeAndFlush(frameWrapper(originStreamId, frame));
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
        int originStreamId = getOriginStreamId(streamId);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} : read windows update frame streamId:{} increment {}", connectionContext, originStreamId,
                         windowSizeIncrement);
        }
        connectionContext.clientChannel().writeAndFlush(
            frameWrapper(originStreamId,
                         new DefaultHttp2WindowUpdateFrame(windowSizeIncrement)));
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                               Http2Flags flags, ByteBuf payload) {
    }

    private int getUpstreamStreamId(int streamId) {
        if (streamId == 0) {
            return streamId;
        }
        return streams.computeIfAbsent(streamId, ignore -> currentStreamId.getAndAdd(2));
    }

    private int getOriginStreamId(int streamId) {
        if (streamId == 0) {
            return streamId;
        }
        if (!streams.inverse().containsKey(streamId)) {
            throw new IllegalStateException("No stream found: " + streamId);
        }
        return streams.inverse().get(streamId);
    }

    private void log(Http2FrameWrapper<?> frame) {
        if (LOGGER.isTraceEnabled()) {
            Http2Frame http2Frame = frame.frame();
            int streamId = frame.streamId();
            boolean endOfStream = frame.isEndStream();

            if (http2Frame instanceof Http2HeadersFrame) {
                LOGGER.trace("{} : sending http2 headers streamId:{} headers:{}",
                             connectionContext, streamId, ((Http2HeadersFrame) http2Frame).headers());
            } else if (http2Frame instanceof Http2DataFrame) {
                LOGGER.trace("{} : sending http2 data frame streamId:{} length:{} endOfStream:{}",
                             connectionContext, streamId, ((Http2DataFrame) http2Frame).content().readableBytes(),
                             endOfStream);
            } else if (http2Frame instanceof Http2SettingsFrame) {
                LOGGER.trace("{} : sending http2 settings frame streamId:{} settings:{}",
                             connectionContext, streamId, ((Http2SettingsFrame) http2Frame).settings());
            } else if (http2Frame instanceof Http2WindowUpdateFrame) {
                LOGGER.trace("{} : sending http2 windows update frame streamId:{} settings:{}",
                             connectionContext, streamId, ((Http2WindowUpdateFrame) http2Frame).windowSizeIncrement());
            } else if (http2Frame instanceof Http2ResetFrame) {
                LOGGER.trace("{} : sending http2 reset frame streamId:{} errorCode:{}",
                             connectionContext, streamId, ((Http2ResetFrame) http2Frame).errorCode());

            } else if (http2Frame instanceof Http2GoAwayFrame) {
                Http2GoAwayFrame goAwayFrame = (Http2GoAwayFrame) http2Frame;
                LOGGER.trace("{} : sending http2 go away frame streamId:{} errorCode:{} data:{}",
                             connectionContext, streamId, goAwayFrame.errorCode(),
                             ByteBufUtil.prettyHexDump(goAwayFrame.content()));
            } else {
                LOGGER.trace("{} : unkknown http2 frame streamId:{} name:{} type:{}",
                             connectionContext, streamId, http2Frame.name(), http2Frame.getClass());
            }
        }
    }
}