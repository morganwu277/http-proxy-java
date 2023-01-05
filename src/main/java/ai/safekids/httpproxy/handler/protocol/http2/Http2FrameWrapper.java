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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2WindowUpdateFrame;
import io.netty.util.ReferenceCountUtil;

import java.util.Objects;

public class Http2FrameWrapper<T extends Http2Frame> {
    protected int streamId;
    protected T frame;

    public Http2FrameWrapper(int streamId, T frame) {
        this.streamId = streamId;
        this.frame = frame;
    }

    public static Http2DataFrameWrapper frameWrapper(int streamId, Http2DataFrame frame) {
        return new Http2DataFrameWrapper(streamId, frame);
    }

    public static <T extends Http2Frame> Http2FrameWrapper<T> frameWrapper(
        int streamId, T frame) {
        return new Http2FrameWrapper<T>(streamId, frame);
    }

    /**
     * Check if the msg is a wrapper of a frame.
     *
     * @param msg        the msg
     * @param frameClass the frame class
     * @param <T>        the Http2Frame class
     * @return {@code true} if msg is a wrapper for {@code frameClass}
     */
    public static <T extends Http2Frame> boolean isFrame(Object msg, Class<T> frameClass) {
        if (!(msg instanceof Http2FrameWrapper)) {
            return false;
        }
        Http2FrameWrapper<?> frameWrapper = (Http2FrameWrapper<?>) msg;
        return frameClass.isAssignableFrom(frameWrapper.frame.getClass());
    }

    public int streamId() {
        return streamId;
    }

    /**
     * Get the origin http2 frame from a wrapper.
     *
     * @param msg the msg
     * @return the origin http2 frame
     */
    public static Http2Frame frame(Object msg) {
        if (!(msg instanceof Http2FrameWrapper)) {
            throw new IllegalStateException("The msg must be a Http2FrameWrapper");
        }
        return ((Http2FrameWrapper<?>) msg).frame();
    }

    public T frame() {
        return frame;
    }

    @SuppressWarnings({ "unchecked", "unused" })
    public <C extends Http2Frame> C frame(Class<C> frameType) {
        return (C) frame;
    }

    public boolean isHeaders() {
        return frame instanceof Http2HeadersFrame;
    }

    public boolean isData() {
        return frame instanceof Http2DataFrame;
    }

    public boolean isEndStream() {
        if (isHeaders()) {
            return ((Http2HeadersFrame) frame).isEndStream();
        }
        if (isData()) {
            return ((Http2DataFrame) frame).isEndStream();
        }
        return false;
    }

    /**
     * Writes toe frame.
     *
     * @param ctx      the ctx
     * @param encoder  the encoder
     * @param streamId the stream id
     * @param promise  the promise
     */
    public ChannelFuture writeAndRetain(ChannelHandlerContext ctx, Http2FrameWriter encoder, int streamId,
                                        ChannelPromise promise) {
        try {
            if (frame instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
                return encoder.writeHeaders(ctx, streamId, headersFrame.headers(), headersFrame.padding(),
                                            headersFrame.isEndStream(), promise);
            } else if (frame instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) frame;
                return encoder.writeData(ctx, streamId, dataFrame.content(), dataFrame.padding(),
                                         dataFrame.isEndStream(), promise);
            } else if (frame instanceof Http2ResetFrame) {
                Http2ResetFrame resetFrame = (Http2ResetFrame) frame;
                return encoder.writeRstStream(ctx, streamId, resetFrame.errorCode(), promise);
            } else if (frame instanceof Http2WindowUpdateFrame) {
                Http2WindowUpdateFrame windowUpdateFrame = (Http2WindowUpdateFrame) frame;
                return encoder.writeWindowUpdate(ctx, streamId, windowUpdateFrame.windowSizeIncrement(), promise);
            } else if (frame instanceof Http2SettingsFrame) {
                Http2SettingsFrame settingsFrame = (Http2SettingsFrame) frame;
                return encoder.writeSettings(ctx, settingsFrame.settings(), promise);
            } else {
                return promise.setSuccess();
            }
        } finally {
            ReferenceCountUtil.retain(frame);
        }
    }

    public ChannelFuture write(ChannelHandlerContext ctx, Http2FrameWriter encoder, int streamId,
                               ChannelPromise promise) {
        if (frame instanceof Http2HeadersFrame) {
            Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
            return encoder.writeHeaders(ctx, streamId, headersFrame.headers(), headersFrame.padding(),
                                        headersFrame.isEndStream(), promise);
        } else if (frame instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) frame;
            return encoder.writeData(ctx, streamId, dataFrame.content(), dataFrame.padding(),
                                     dataFrame.isEndStream(), promise);
        } else if (frame instanceof Http2ResetFrame) {
            Http2ResetFrame resetFrame = (Http2ResetFrame) frame;
            return encoder.writeRstStream(ctx, streamId, resetFrame.errorCode(), promise);
        } else if (frame instanceof Http2WindowUpdateFrame) {
            Http2WindowUpdateFrame windowUpdateFrame = (Http2WindowUpdateFrame) frame;
            return encoder.writeWindowUpdate(ctx, streamId, windowUpdateFrame.windowSizeIncrement(), promise);
        } else if (frame instanceof Http2SettingsFrame) {
            Http2SettingsFrame settingsFrame = (Http2SettingsFrame) frame;
            return encoder.writeSettings(ctx, settingsFrame.settings(), promise);
        } else {
            return promise.setSuccess();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Http2FrameWrapper<?> that = (Http2FrameWrapper<?>) o;
        return streamId == that.streamId && frame.equals(that.frame);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamId, frame);
    }

    @Override
    public String toString() {
        return frame.name() + " Frame: streamId=" + streamId;
    }
}
