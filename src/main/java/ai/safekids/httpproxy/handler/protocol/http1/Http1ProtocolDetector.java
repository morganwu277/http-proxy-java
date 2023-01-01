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

package ai.safekids.httpproxy.handler.protocol.http1;

import ai.safekids.httpproxy.Protocols;
import ai.safekids.httpproxy.handler.protocol.ProtocolDetector;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public class Http1ProtocolDetector implements ProtocolDetector {

    private static final int MAX_READ_BYTES = 100;
    private static final Pattern HTTP_FIRST_LINE = Pattern.compile("[A-Z]+\\s\\S+\\sHTTP/1\\.1");

    public static final Http1ProtocolDetector INSTANCE = new Http1ProtocolDetector();

    @Override
    public Optional<String> detect(ByteBuf msg) {
        int readBytes = Math.min(MAX_READ_BYTES, msg.readableBytes());
        if (readBytes == 0) {
            return Optional.empty();
        }
        byte[] bytes = new byte[readBytes];
        for (int pos = 0; pos < readBytes; pos++) {
            bytes[pos] = msg.getByte(pos);
            if (bytes[pos] == '\r') {
                bytes = Arrays.copyOf(bytes, pos);
                break;
            }
        }
        String line = new String(bytes);
        if (HTTP_FIRST_LINE.matcher(line).matches()) {
            return Optional.of(Protocols.HTTP_1);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return Protocols.HTTP_1;
    }
}
