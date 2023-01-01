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

import ai.safekids.httpproxy.exception.NitmProxyException;
import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;

import java.util.ArrayList;
import java.util.List;

import static ai.safekids.httpproxy.handler.protocol.http2.Http2FrameWrapper.*;
import static com.google.common.net.HttpHeaders.*;
import static java.util.stream.Collectors.*;

public class Http2FramesWrapper {

    private int streamId;
    private Http2HeadersFrame headersFrame;
    private List<Http2DataFrame> dataFrames;

    public Http2FramesWrapper(Builder builder) {
        this.streamId = builder.streamId;
        this.headersFrame = builder.headersFrame;
        this.dataFrames = builder.dataFrames;
    }

    public static Builder builder(int streamId) {
        return new Builder(streamId);
    }

    public int getStreamId() {
        return streamId;
    }

    public Http2HeadersFrame getHeaders() {
        return headersFrame;
    }

    public List<Http2DataFrame> getData() {
        return dataFrames;
    }

    public List<Http2FrameWrapper<?>> getAllFrames() {
        return ImmutableList.<Http2FrameWrapper<?>>builder()
                .add(frameWrapper(streamId, headersFrame))
                .addAll(dataFrames.stream().map(frame -> frameWrapper(streamId, frame)).collect(toList()))
                .build();
    }

    public static class Builder {
        private int streamId;
        private Http2HeadersFrame headersFrame;
        private List<Http2DataFrame> dataFrames = new ArrayList<>();

        private Builder(int streamId) {
            this.streamId = streamId;
        }

        public Builder request(FullHttpRequest request) {
            headersFrame = new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(),
                    request.content().readableBytes() == 0);
            headersFrame.headers()
                        .authority(request.headers().get(HOST))
                        .path(request.uri())
                        .method(request.method().name())
                        .scheme("https");
            request.headers().forEach(entry -> headersFrame
                    .headers().add(entry.getKey().toLowerCase(), entry.getValue()));
            if (request.content().readableBytes() > 0) {
                dataFrames.add(new DefaultHttp2DataFrame(request.content(), true));
            }
            return this;
        }

        public Builder response(FullHttpResponse response) {
            headersFrame = new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(),
                    response.content().readableBytes() == 0);
            headersFrame.headers().status(response.status().codeAsText());
            response.headers().forEach(entry -> headersFrame
                    .headers().add(entry.getKey().toLowerCase(), entry.getValue()));
            if (response.content().readableBytes() > 0) {
                dataFrames.add(new DefaultHttp2DataFrame(response.content(), true));
            }
            return this;
        }

        public Builder headers(Http2HeadersFrame headersFrame) {
            this.headersFrame = headersFrame;
            return this;
        }

        public Builder data(Http2DataFrame dataFrame) {
            dataFrames.add(dataFrame);
            return this;
        }

        public Builder data(List<Http2DataFrame> dataFrames) {
            this.dataFrames.addAll(dataFrames);
            return this;
        }

        public Http2FramesWrapper build() {
            if (headersFrame == null) {
                throw new NitmProxyException("null headers");
            }
            boolean ended = headersFrame.isEndStream();
            for (Http2DataFrame data : dataFrames) {
                if (ended) {
                    throw new NitmProxyException("stream was ended, but found another data frame");
                }
                ended = data.isEndStream();
            }
            if (!ended) {
                throw new NitmProxyException("stream not ended");
            }
            return new Http2FramesWrapper(this);
        }
    }
}
