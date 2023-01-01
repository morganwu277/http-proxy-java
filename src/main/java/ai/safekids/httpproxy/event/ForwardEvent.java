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

package ai.safekids.httpproxy.event;

import ai.safekids.httpproxy.Address;
import ai.safekids.httpproxy.ConnectionContext;

public class ForwardEvent {
    private Address client;
    private Address server;

    private long requestBodySize;
    private long requestTime;

    // response
    private long responseBodySize;
    private long responseTime;

    private long timeSpent;

    private ForwardEvent(Builder builder) {
        client = builder.client;
        server = builder.server;

        requestBodySize = builder.requestBodySize;
        requestTime = builder.requestTime;

        responseBodySize = builder.responseBodySize;
        responseTime = builder.responseTime;

        timeSpent = builder.responseTime - builder.requestTime;
    }

    public static Builder builder(ConnectionContext ctx) {
        return new Builder(ctx);
    }

    public Address getClient() {
        return client;
    }

    public Address getServer() {
        return server;
    }

    public long getRequestBodySize() {
        return requestBodySize;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public long getResponseBodySize() {
        return responseBodySize;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public long getTimeSpent() {
        return timeSpent;
    }

    public static class Builder {
        private Address client;
        private Address server;

        // request
        private long requestBodySize;
        private long requestTime;

        // response
        private long responseBodySize;
        private long responseTime;

        private Builder(ConnectionContext ctx) {
            client = ctx.getClientAddr();
            server = ctx.getServerAddr();
        }

        public Builder requestBodySize(long requestBodySize) {
            this.requestBodySize = requestBodySize;
            return this;
        }

        public Builder addRequestBodySize(long delta) {
            this.requestBodySize += delta;
            return this;
        }

        public Builder requestTime(long requestTime) {
            this.requestTime = requestTime;
            return this;
        }

        public Builder responseBodySize(long responseBodySize) {
            this.responseBodySize = responseBodySize;
            return this;
        }

        public Builder addResponseBodySize(long delta) {
            this.responseBodySize += delta;
            return this;
        }

        public Builder responseTime(long responseTime) {
            this.responseTime = responseTime;
            return this;
        }

        public ForwardEvent build() {
            return new ForwardEvent(this);
        }
    }
}
