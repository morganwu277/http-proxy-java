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

package ai.safekids.httpproxy.testing;

import ai.safekids.httpproxy.handler.protocol.http2.Http2FrameWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.assertj.core.api.AbstractIterableAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.util.Streams;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.util.ReferenceCountUtil.*;
import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.*;

public class ChannelMessagesAssert
    extends AbstractIterableAssert<ChannelMessagesAssert, Queue<Object>, Object, ObjectAssert<Object>> {
  public ChannelMessagesAssert(Queue<Object> actual) {
    super(actual, ChannelMessagesAssert.class);
  }

  @Override
  protected ObjectAssert<Object> toAssert(Object value, String description) {
    return new ObjectAssert<>(value);
  }

  @Override
  protected ChannelMessagesAssert newAbstractIterableAssert(Iterable<?> iterable) {
    return new ChannelMessagesAssert(
        Streams.stream(iterable).collect(toCollection(ArrayDeque::new)));
  }

  public ChannelMessagesAssert has(Class<?> type) {
    assertThat(actual).isNotEmpty();
    assertThat(actual.peek()).isInstanceOf(type);
    return this;
  }

  public ByteBufAssert hasByteBuf() {
    has(ByteBuf.class);
    return new ByteBufAssert((ByteBuf) actual.poll());
  }

  public Http2FrameWrapperAssert hasHttp2Frame() {
    has(Http2FrameWrapper.class);
    return new Http2FrameWrapperAssert((Http2FrameWrapper<?>) actual.poll());
  }

  public ResponseAssert hasResponse() {
    FullHttpResponse fullResponse = null;
    while (!actual.isEmpty() && actual.peek() instanceof HttpObject) {
      HttpObject httpObject = (HttpObject) actual.poll();
      if (httpObject instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) httpObject;
        fullResponse = new DefaultFullHttpResponse(response.protocolVersion(),
            response.status(), Unpooled.buffer(), response.headers(), new DefaultHttpHeaders());
      }
      if (httpObject instanceof HttpContent) {
        assertThat(fullResponse).isNotNull();
        fullResponse.content().writeBytes(((HttpContent) httpObject).content());
        release(httpObject);
      }
      if (httpObject instanceof LastHttpContent) {
        break;
      }
    }
    return new ResponseAssert(fullResponse).isNotNull();
  }

  public RequestAssert hasRequest() {
    assertThat(actual).isNotEmpty();
    assertThat(actual.peek()).isInstanceOf(FullHttpRequest.class);
    return new RequestAssert((FullHttpRequest) actual.poll());
  }

  public ObjectAssert<Object> peek() {
    return new ObjectAssert<>(actual.peek());
  }
}
