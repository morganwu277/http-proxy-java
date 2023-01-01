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

package ai.safekids.httpproxy.http;

import com.google.common.base.Joiner;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.net.HttpHeaders.*;
import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static java.lang.String.*;
import static java.nio.charset.StandardCharsets.*;

public class HttpUtil {

    public static final int HTTP_PORT = 80;
    public static final int HTTPS_PORT = 443;
    public static final HttpVersion HTTP_2 = new HttpVersion("http/2.0", true);

    private HttpUtil() {
    }

    public static FullHttpRequest defaultRequest() {
        return request(HttpVersion.HTTP_1_1, HttpMethod.GET, "localhost", "/");
    }

    /**
     * Create a http request.
     *
     * @param version the http version
     * @param method  the http method
     * @param host    the host
     * @param url     the url
     * @return the http request
     */
    public static FullHttpRequest request(HttpVersion version, HttpMethod method, String host,
            String url) {
        FullHttpRequest request = new DefaultFullHttpRequest(version, method, url);
        request.headers().set(HOST, host);
        return request;
    }

    /**
     * Create a text request.
     *
     * @param version the http version
     * @param method  the http method
     * @param host    the host
     * @param url     the url
     * @return the http request
     */
    public static FullHttpRequest textRequest(HttpVersion version, HttpMethod method, String host,
            String url, String body) {
        FullHttpRequest request = new DefaultFullHttpRequest(version, method, url, copiedBuffer(body, UTF_8));
        request.headers()
               .set(HOST, host)
               .set(CONTENT_LENGTH, request.content().readableBytes())
               .set(CONTENT_TYPE, TEXT_PLAIN);
        return request;
    }

    /**
     * Create a json request.
     *
     * @param version the http version
     * @param method  the http method
     * @param host    the host
     * @param url     the url
     * @return the http request
     */
    public static FullHttpRequest jsonRequest(HttpVersion version, HttpMethod method, String host,
            String url, String json) {
        FullHttpRequest request = new DefaultFullHttpRequest(version, method, url);
        byte[] bodyBytes = json.getBytes(UTF_8);
        request.headers()
               .set(HOST, host)
               .set(CONTENT_LENGTH, bodyBytes.length)
               .set(CONTENT_TYPE, APPLICATION_JSON);
        request.content().writeBytes(bodyBytes);
        return request;
    }

    public static FullHttpResponse defaultResponse(String content) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK, copiedBuffer(content, UTF_8));
        response.headers().add(CONTENT_TYPE, "text/plain");
        response.headers().add(CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    /**
     * Create a json response.
     *
     * @param alloc   the allocator
     * @param version the http version
     * @param json    the json
     * @return the response
     */
    public static FullHttpResponse jsonResponse(
            ByteBufAllocator alloc, HttpVersion version, String json) {
        return response(alloc, OK, version, APPLICATION_JSON, json);
    }

    /**
     * Create a text response.
     *
     * @param alloc   the allocator
     * @param version the http version
     * @param text    the text
     * @return the response
     */
    public static FullHttpResponse textResponse(
            ByteBufAllocator alloc, HttpVersion version, String text) {
        return response(alloc, OK, version, TEXT_PLAIN, text);
    }

    /**
     * Create a json response.
     *
     * @param alloc   the allocator
     * @param version the http version
     * @param status  the status
     * @return the response
     */
    public static FullHttpResponse errorResponse(
            ByteBufAllocator alloc, HttpVersion version, HttpResponseStatus status) {
        return response(alloc, status, version, TEXT_PLAIN, "");
    }

    /**
     * Create a response.
     *
     * @param alloc       the allocator
     * @param status      the status
     * @param version     the http version
     * @param contentType the content type
     * @param body        the body
     * @return the response
     */
    public static FullHttpResponse response(ByteBufAllocator alloc, HttpResponseStatus status,
            HttpVersion version,
            AsciiString contentType, String body) {
        FullHttpResponse response = new DefaultFullHttpResponse(version, status, alloc.buffer());
        byte[] bodyBytes = body.getBytes(UTF_8);
        response.headers()
                .set(CONTENT_TYPE, contentType)
                .set(CONTENT_LENGTH, bodyBytes.length);
        response.content().writeBytes(bodyBytes);
        return response;
    }

    public static ByteBuf toBytes(FullHttpRequest request) {
        String req = String.format("%s %s %s\r\n%s\r\n",
                request.method(),
                request.uri(),
                request.protocolVersion(),
                toString(request.headers()));
        return Unpooled.buffer().writeBytes(req.getBytes());
    }

    /**
     * Encode the request into a buffer.
     *
     * @param buffer  the buffer
     * @param request the request
     */
    public static void write(ByteBuf buffer, HttpRequest request) {
        buffer.writeCharSequence(
                format("%s %s %s\r\n", request.method(), request.uri(), request.protocolVersion()),
                UTF_8);
        write(buffer, request.headers());
        buffer.writeCharSequence("\r\n", US_ASCII);
    }

    public static String toString(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return "";
        }

        List<String> headerStrings = headers.entries().stream()
                                            .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
                                            .collect(Collectors.toList());
        return Joiner.on("\r\n").join(headerStrings) + "\r\n";
    }

    /**
     * Encode the headers into a buffer.
     *
     * @param buffer  the buffer
     * @param headers the headers
     */
    public static void write(ByteBuf buffer, HttpHeaders headers) {
        if (headers.isEmpty()) {
            return;
        }
        headers.entries().stream()
               .map(entry -> format("%s: %s\r\n", entry.getKey(), entry.getValue()))
               .forEach(line -> buffer.writeCharSequence(line, UTF_8));
    }
}
