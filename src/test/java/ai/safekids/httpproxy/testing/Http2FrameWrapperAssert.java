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
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.ReferenceCountUtil;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.InstanceOfAssertFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

@SuppressWarnings("rawtypes")
public class Http2FrameWrapperAssert extends AbstractObjectAssert<Http2FrameWrapperAssert, Http2FrameWrapper> {

  public static final InstanceOfAssertFactory<Http2FrameWrapper, Http2FrameWrapperAssert> HTTP2_FRAME = new InstanceOfAssertFactory<>(
      Http2FrameWrapper.class, Http2FrameWrapperAssert::new);

  public Http2FrameWrapperAssert(Http2FrameWrapper<?> actual) {
    super(actual, Http2FrameWrapperAssert.class);
  }

  public Http2FrameWrapperAssert hasStreamId(int streamId) {
    assertEquals(actual.streamId(), streamId);
    return this;
  }

  public Http2HeadersFrameAssert isHeadersFrame() {
    is(Http2HeadersFrame.class);
    return new Http2HeadersFrameAssert((Http2HeadersFrame) actual.frame());
  }

  public Http2DataFrameAssert isDataFrame() {
    is(Http2DataFrame.class);
    return new Http2DataFrameAssert((Http2DataFrame) actual.frame());
  }

  public Http2FrameWrapperAssert is(Class<? extends Http2Frame> frameClass) {
    assertThat(actual.frame()).isInstanceOf(frameClass);
    return this;
  }

  public void release() {
    ReferenceCountUtil.release(actual);
  }
}
