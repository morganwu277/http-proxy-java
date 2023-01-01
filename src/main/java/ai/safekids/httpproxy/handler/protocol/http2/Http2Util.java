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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class Http2Util {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http2Util.class);

    private Http2Util() {
    }

    public static Http2Headers frameToHeaders(List<Http2Frame> frames) {
        Http2Headers headers =
            frames.stream()
                  .filter(f -> f instanceof Http2HeadersFrame)
                  .map(m -> (Http2HeadersFrame) m)
                  .filter(Objects::nonNull)
                  .map(a -> a.headers())
                  .reduce(new DefaultHttp2Headers(), (a, b) -> a.add(b));
        return headers;
    }

    public static boolean doesStreamEndOnHeaders(List<Http2HeadersFrame> frames) {
        return frames.stream().filter(f -> f.isEndStream()).filter(Objects::nonNull).map(v -> v.isEndStream())
                     .findFirst().orElse(false);
    }

    public static FullHttpResponse decompressResponse(FullHttpResponse response) throws Exception {
        int contentLength = Math.max(response.content().readableBytes(), 10048576);
        EmbeddedChannel embeddedChannel =
            new EmbeddedChannel(new HttpContentDecompressor(), new HttpObjectAggregator(contentLength));
        embeddedChannel.writeInbound(response);
        FullHttpResponse decodedResponse = (FullHttpResponse) embeddedChannel.readInbound();

        //check for decoding errors
        DecoderResult decoderResult = decodedResponse.decoderResult();
        if (decoderResult != null) {
            if (decoderResult.isFailure()) {
                LOGGER.error("Decoding failed for content type: {} due to",
                             response.headers().get(HttpHeaderNames.CONTENT_ENCODING), decoderResult.cause());
            }
        }
        embeddedChannel.checkException();
        return decodedResponse;
    }

    public static HttpContent compressResponse(FullHttpResponse response, String acceptEncoding) throws Exception {
        int contentLength = Math.max(response.content().readableBytes(), 10048576);

        EmbeddedChannel embeddedChannel =
            new EmbeddedChannel(new HttpContentCompressor());

        EmbeddedChannel embeddedChannel2 =
            new EmbeddedChannel(new HttpObjectAggregator(contentLength));

        DefaultHttpRequest request =
            new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");

        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, acceptEncoding);
        response.headers().remove(HttpHeaderNames.CONTENT_ENCODING);

        embeddedChannel.writeInbound(request);
        embeddedChannel.writeOutbound(response);

        while (true) {
            HttpObject object = (HttpObject) embeddedChannel.readOutbound();
            embeddedChannel2.writeInbound(object);
            if (object == null || object instanceof LastHttpContent) {
                break;
            }
        }

        FullHttpResponse fullHttpResponse = (FullHttpResponse) embeddedChannel2.readInbound();
        embeddedChannel.checkException();
        embeddedChannel.finishAndReleaseAll();
        return fullHttpResponse;
    }
}
