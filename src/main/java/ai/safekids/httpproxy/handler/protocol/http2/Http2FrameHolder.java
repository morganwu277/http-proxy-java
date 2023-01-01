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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class Http2FrameHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http2FrameHolder.class);

    int streamId;

    Http2FrameSettings frameSettings = new Http2FrameSettings();
    List<Http2Frame> frames;

    Http2Headers headers;

    boolean streamEndedOnHeaders;

    boolean isCompressed;

    public Http2FrameHolder(int streamId, List<Http2Frame> frames) {
        this.streamId = streamId;
        this.frames = frames;
        init();
    }

    void init() {
        Integer maxFrameSize = 0;

        headers = new DefaultHttp2Headers();

        for (Http2Frame frame : frames) {
            if (frame instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
                if (headersFrame.isEndStream()) {
                    streamEndedOnHeaders = true;
                }
                headers.add(headersFrame.headers());
            }

            if (frame instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) frame;
                maxFrameSize = Math.max(dataFrame.content().readableBytes(), maxFrameSize);
            }
        }

        if (maxFrameSize > 0) {
            maxFrameSize = Math.max(maxFrameSize, Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
            frameSettings.setMaxFrameSize(maxFrameSize);
        }

        isCompressed = headers.contains(HttpHeaderNames.CONTENT_ENCODING);
    }

    public List<Http2DataFrame> getDataFrames() {
        return frames.stream().filter(f -> f instanceof Http2DataFrame).map(m -> (Http2DataFrame) m)
                     .collect(Collectors.toList());
    }

    public String getContentEncoding() {
        CharSequence res = headers.get(HttpHeaderNames.CONTENT_ENCODING);
        return res != null? res.toString() : null;
    }

    public String getContentType() {
        CharSequence res = headers.get(HttpHeaderNames.CONTENT_TYPE);
        return res != null? res.toString() : null;
    }

    public ByteBuf getCompositeDateBuffWithAlloc(ChannelHandlerContext ctx) {
        List<Http2DataFrame> dataFrames = getDataFrames();
        CompositeByteBuf byteBuff = ctx.alloc().compositeBuffer();
        dataFrames.forEach(d -> byteBuff.addComponent(true, d.content().retainedDuplicate()));
        return byteBuff;
    }

    public List<Http2Frame> getFrames() {
        return frames;
    }

    public void setFrames(List<Http2Frame> frames) {
        this.frames = frames;
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public Http2FrameSettings getFrameSettings() {
        return frameSettings;
    }

    public void setFrameSettings(
        Http2FrameSettings frameSettings) {
        this.frameSettings = frameSettings;
    }

    public Http2Headers getHeaders() {
        return headers;
    }

    public void setHeaders(Http2Headers headers) {
        this.headers = headers;
    }

    public boolean isCompressed() {
        return isCompressed;
    }

    public void setCompressed(boolean compressed) {
        isCompressed = compressed;
    }

    public boolean isStreamEndedOnHeaders() {
        return streamEndedOnHeaders;
    }
}