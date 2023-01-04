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
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http2.Http2DataFrame;

public class Http2DataFrameWrapper
        extends Http2FrameWrapper<Http2DataFrame>
        implements ByteBufHolder {

    public Http2DataFrameWrapper(int streamId, Http2DataFrame frame) {
        super(streamId, frame);
    }

    @Override
    public ByteBuf content() {
        return frame.content();
    }

    @Override
    public Http2DataFrameWrapper copy() {
        return replace(frame.content().copy());
    }

    @Override
    public Http2DataFrameWrapper duplicate() {
        return replace(frame.content().duplicate());
    }

    @Override
    public Http2DataFrameWrapper retainedDuplicate() {
        return replace(frame.content().retainedDuplicate());
    }

    @Override
    public Http2DataFrameWrapper replace(ByteBuf content) {
        return new Http2DataFrameWrapper(streamId, frame.replace(content));
    }

    @Override
    public int refCnt() {
        return frame.refCnt();
    }

    @Override
    public Http2DataFrameWrapper retain() {
        frame.retain();
        return this;
    }

    @Override
    public Http2DataFrameWrapper retain(int increment) {
        frame.retain(increment);
        return this;
    }

    @Override
    public Http2DataFrameWrapper touch() {
        frame.touch();
        return this;
    }

    @Override
    public Http2DataFrameWrapper touch(Object hint) {
        frame.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return frame.release();
    }

    @Override
    public boolean release(int decrement) {
        return frame.release(decrement);
    }

    @Override
    public String toString() {
        return frame.name() + " Frame:" +
               " streamId=" + streamId +
               " endStream=" + frame.isEndStream() +
               " length=" + frame.content().readableBytes();
    }
}